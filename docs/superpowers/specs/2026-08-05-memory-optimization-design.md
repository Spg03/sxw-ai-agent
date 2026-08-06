# 记忆系统优化设计

> 创建日期：2026-08-05 ｜ 状态：待实施

## 一、现状

后端已具备两套可用记忆雏形：

- **长期记忆**：`MemoryItem` → PostgreSQL `ai_memory_item` 表，通过 `PromptAssembler` 注入 System Prompt，由 Hermes 流水线（`HermesAnalyzer` → `HermesCandidate` → `HermesApplier`）自动写入
- **对话历史**：`JdbcChatMemoryRepository`（PostgreSQL `ai_chat_memory`）+ `ManusMemoryStore`（内存 LRU）+ `BaseAgent.messageList`（实例内存）三套并存

### 已知问题

| # | 问题 | 严重度 |
|---|------|--------|
| 1 | `HermesApplier` 绕过审核，直接将候选写入 ACTIVE；`MemoryService.approve/reject` 是死代码 | 高 |
| 2 | 对话历史三套存储并存（JDBC / LRU / instance list），切换 Runtime 时上下文不一致 | 高 |
| 3 | `MemorySelector` 用「前 3 个词」做关键词匹配，中文场景几乎无检索价值 | 中 |
| 4 | 反序列化失败生成 `UserMessage("[deserialization error]")` 注入模型上下文 | 中 |
| 5 | `MemoryPolicy.maxHistoryMessages` 配置了但 `ToolUseLoopRuntime` 硬编码 `MAX_HISTORY_MESSAGES=100` 不读取 | 中 |
| 6 | `JdbcChatMemoryRepository.saveAll` 用 DELETE + INSERT 全量替换，写放大且并发不安全 | 中 |
| 7 | Assistant 回复中出现「记住/偏好/喜欢/已记录」即触发候选创建，无写入校验 | 低 |
| 8 | 无记忆 CRUD API，无法手动管理 | 低 |

## 二、目标架构

```
                        用户消息
                           │
                           ▼
                  ConversationService
                           │
               ┌───────────┴───────────┐
               ▼                       ▼
       PostgreSQL 保存消息       ContextBuilder
                                       │
                     ┌─────────────────┼─────────────────┐
                     ▼                 ▼                 ▼
              会话滚动摘要        最近消息          长期记忆检索
                     │                 │                 │
                     └─────────────────┼─────────────────┘
                                       ▼
                                   LLM 调用
                                       │
                                       ▼
                               保存 Assistant 消息
                                       │
                                       ▼
                               HermesAnalyzer
                                       │
                                       ▼
                          HermesCandidate(PENDING)
                                       │
                          ┌────────────┴────────────┐
                          ▼                         ▼
                    自动批准                  用户确认
               明确记忆指令               推断型候选记忆
                          │                         │
                          └────────────┬────────────┘
                                       ▼
                               HermesApplier
                                       │
                                       ▼
                                ai_memory_item
```

核心原则：**`HermesApplier` 只能应用已经批准的候选，不能直接决定候选是否应该生效。**

## 三、分阶段实施计划

### 阶段一：修复可靠性（4 项改动）

**目标**：消除脏数据写入和上下文污染。

#### 1.1 删除 Assistant 文案启发式候选

- 删除 `HermesAnalyzer` 中基于「记住/偏好/喜欢/已记录」关键词的自动候选创建
- 保留用户显式指令触发（如 `!remember` / `/remember` 前缀）
- 候选来源限定为：用户原始消息、管理员配置、可信工具执行结果

#### 1.2 HermesApplier 增加前置校验

- `apply()` 仅接受 `CandidateStatus.APPROVED`，其他状态抛 `IllegalStateException`
- 使用 `SELECT ... FOR UPDATE` 或乐观锁防并发
- 同一个候选只能应用一次
- `applyMemory()` 成功后调用 `candidate.markApplied(memoryId)` 更新候选状态为 `APPLIED`

#### 1.3 接通 MemoryService 审核方法

- `MemoryService.approve(memoryId, reviewedBy)` 更新状态为 ACTIVE 而非直接写库
- `MemoryService.reject(memoryId, reviewedBy)` 更新状态为 ARCHIVED
- `HermesApplier` 改为调用 `MemoryService.upsert(candidate)` 而非直接 `MemoryRepository.save()`
- 外部组件禁止直接调用 `MemoryRepository.save/updateStatus`

#### 1.4 修复反序列化污染

- `JdbcChatMemoryRepository.deserializeMessage()` 失败时返回 `Optional.empty()` 而非伪造 UserMessage
- 新增 `serialization_status` 字段：`NORMAL / CORRUPTED / SKIPPED`
- 失败消息记录 `markCorrupted()` + Micrometer 计数
- 调用方 `flatMap(Optional::stream)` 过滤坏消息

### 阶段二：统一对话历史存储

**目标**：PostgreSQL 作为唯一事实源。

#### 2.1 定义统一接口

```java
public interface ConversationHistoryStore {
    List<Message> loadRecent(String conversationId, int limit);
    void append(String conversationId, Message message);
    void appendBatch(String conversationId, List<Message> messages);
    void clear(String conversationId);
    long count(String conversationId);
}
```

#### 2.2 PostgreSQL 实现

- `PostgresConversationHistoryStore` 实现上述接口
- 消息表改为 append-only（新增 `ai_chat_message` 表，保留旧表兼容）
- 使用 `sequence_no` 保证消息顺序

#### 2.3 降级旧链路

- `ManusMemoryStore` 改为 `ConversationHistoryStore` 的适配器，内部委托 PostgreSQL
- 标记 `@Deprecated`，后续删除 LRU Map
- `BaseAgent.messageList` 改为请求级 `AgentExecutionContext`，单例 Bean 不再持有用户消息

### 阶段三：滚动摘要 + 上下文裁剪

**目标**：用「摘要 + 最近 N 条」替代「最近 100 条」。

#### 3.1 新增摘要表和服务

- `ai_conversation_summary` 表：`conversation_id, summary, summarized_until_sequence, version, updated_at`
- `ConversationSummaryService`：未摘要消息 >= 20 条时触发 LLM 摘要
- 摘要只覆盖已完结的 Turn

#### 3.2 统一 HistoryPolicy

- 新配置类 `HistoryPolicy`（`@ConfigurationProperties(prefix = "agent.history")`）
- 字段：`recentMessages(20)`, `summarizeThreshold(30)`, `maxSummaryChars(1200)`, `maxToolMessages(20)`
- 删除 `ToolUseLoopRuntime.MAX_HISTORY_MESSAGES = 100` 硬编码
- `MemoryPolicy` 不再混管聊天历史

#### 3.3 按 Turn 裁剪

- 抽离 `HistoryTrimmer` 组件
- 裁剪原则：不留下孤立 ToolResponse、不留下无响应 ToolCall、SystemMessage 不反复保存
- 一个完整 Turn = `UserMessage → AssistantMessage → [ToolCall* → ToolResponse*] → [AssistantMessage*]`

### 阶段四：改进长期记忆检索

**目标**：关键词匹配 → 结构化标签 + 轻量相关性排序。

#### 4.1 数据库扩展

```sql
ALTER TABLE ai_memory_item
ADD COLUMN memory_key VARCHAR(128),
ADD COLUMN keywords TEXT[] NOT NULL DEFAULT '{}',
ADD COLUMN importance NUMERIC(5,4) NOT NULL DEFAULT 0.5000,
ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT 'AGENT',
ADD COLUMN scope_id VARCHAR(128),
ADD COLUMN last_accessed_at TIMESTAMPTZ,
ADD COLUMN access_count INTEGER NOT NULL DEFAULT 0;
```

#### 4.2 索引

- GIN 索引 `idx_memory_keywords` ON `keywords`
- `pg_trgm` 索引 `idx_memory_title_trgm` ON `title`、`idx_memory_content_trgm` ON `content`

#### 4.3 加权评分排序

```text
作用域完全匹配       +40
memory_key 命中      +35
keywords 命中        +25
标题相似(pg_trgm)    +15
内容相似(pg_trgm)    +10
BOUNDARY 类型        +20
PREFERENCE 类型      +10
importance × 10
confidence × 5
近期更新             +0～5
```

#### 4.4 两段记忆注入替换全量 Index

- **Always-on（最多 5 条，~300 token）**：BOUNDARY、高优先级 PREFERENCE、核心 PROFILE，每次都注入
- **Relevant（最多 5-8 条，~500 token）**：PROJECT、REFERENCE、普通 USER 事实，按当前问题动态选择
- 取消「每轮固定 top-20 索引全量注入」

### 阶段五：记忆管理 API（后续）

- 候选记忆确认/拒绝 API
- 查看记忆来源（traceId）
- 编辑/禁用/归档/删除正式记忆
- 临时对话（不写入长期记忆）

## 四、暂时不做

- 不引入 Neo4j / 知识图谱
- 不对全部历史做向量化
- 不让所有候选自动 ACTIVE
- 不让模型自行决定记忆权限
- 不从 Assistant 回复中提取事实

## 五、关键文件

| 文件 | 改动 |
|------|------|
| `hermes/HermesApplier.java` | 增加 APPROVED 前置校验 |
| `hermes/HermesAnalyzer.java` | 删除 Assistant 文案启发式 |
| `agent/runtime/ToolUseLoopRuntime.java` | 移除 MAX_HISTORY_MESSAGES=100 硬编码 |
| `infrastructure/memory/ManusMemoryStore.java` | 委托 PostgreSQL Store |
| `infrastructure/memory/JdbcChatMemoryRepository.java` | 反序列化失败返回 Optional.empty() |
| `memory/MemorySelector.java` | 移除「前 3 个词」规则，改用结构化标签+加权评分 |
| `memory/MemoryService.java` | 接通 approve/reject，成为统一门面 |

## 六、验收标准

- `mvn test` 全部通过
- `HermesApplier.apply()` 拒绝 PENDING 状态候选
- 反序列化失败时上下文中不存在 `[deserialization error]`
- `ManusMemoryStore` 底层数据来自 PostgreSQL
- `MemorySelector.isRelevant()` 不再使用前 3 个词
- 清除记忆接口同时清理所有存储
