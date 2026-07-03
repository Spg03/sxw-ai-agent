# 记忆规则

## 概述

记忆系统分为四个概念层次，不要混淆：

| 概念 | 定义 | 存储位置 | 示例 |
|------|------|----------|------|
| **Knowledge** | 项目文档、领域知识、FAQ | PgVector | 恋爱问题文档、工具说明 |
| **Memory** | 用户偏好、反馈规则、项目状态 | 结构化表 | 用户喜欢简洁回答 |
| **Trace** | 运行过程记录 | Trace 表 | 工具调用序列、模型响应 |
| **Eval** | 评测样本 | Eval 表 | 输入输出对、评分 |

## Memory 结构设计

### Index + Detail 模式

借鉴 Claude Code 的记忆系统：`MEMORY.md` 作为轻量索引常驻 Prompt，具体记忆按需加载。

```
MemoryIndex（摘要列表，常驻 Prompt）
  ├── memory_001: "用户偏好简洁回答" (USER, confidence=0.95)
  ├── memory_002: "项目使用 Spring Boot 3" (PROJECT, confidence=0.99)
  └── memory_003: "上次搜索失败原因" (FEEDBACK, confidence=0.80)

MemoryDetail（详细内容，按需加载 topN）
  ├── memory_001: { ruleText: "...", whyText: "...", applyText: "..." }
  └── ...
```

### Memory 类型

```java
public enum MemoryType {
    USER,        // 用户偏好（语言、风格、频率）
    FEEDBACK,    // 用户反馈形成的规则（纠正、确认）
    PROJECT,     // 项目长期状态（技术栈、约定）
    REFERENCE    // 外部引用指针（文档链接、API 地址）
}
```

### Memory 状态

```java
public enum MemoryStatus {
    PENDING,     // 待审核（Hermes 生成）
    ACTIVE,      // 已激活（审核通过）
    EXPIRED,     // 已过期
    ARCHIVED     // 已归档
}
```

## Memory 表设计

```sql
CREATE TABLE ai_memory_item (
    id BIGSERIAL PRIMARY KEY,
    memory_id VARCHAR(64) NOT NULL UNIQUE,
    memory_type VARCHAR(64) NOT NULL,        -- USER / FEEDBACK / PROJECT / REFERENCE
    name VARCHAR(128) NOT NULL,              -- 记忆名称（索引中显示）
    description VARCHAR(512) NOT NULL,       -- 记忆描述（索引中显示）
    rule_text TEXT,                          -- 规则内容
    why_text TEXT,                           -- 形成原因
    apply_text TEXT,                         -- 应用场景
    source_trace_id VARCHAR(64),             -- 来源追踪 ID
    confidence DECIMAL(5,4) DEFAULT 0.5,     -- 置信度
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expired_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(64)
);
```

## 写入规则

### 核心原则：记忆不能自动写入

```
Hermes 分析 Trace → 生成 MemoryCandidate → 用户审核 → 写入 Memory
```

1. **Hermes 只生成候选**，不直接写入记忆
2. **用户必须审核**每个候选（approve / reject）
3. **审核通过后**才能写入 `ai_memory_item`
4. **置信度低于阈值**的候选自动标记为 REJECTED

### 什么值得记忆

| 类型 | 示例 | 优先级 |
|------|------|--------|
| USER | "用户喜欢中文回答" | 高 |
| USER | "用户偏好代码示例" | 高 |
| FEEDBACK | "上次搜索 API 超时，需要重试" | 中 |
| PROJECT | "项目使用 DashScope qwen-plus" | 中 |
| REFERENCE | "Spring AI 文档地址" | 低 |

### 什么不应该记忆

1. 临时任务进度（用 session 管理）
2. 单次对话内容（用 ChatMemory 管理）
3. 原始数据（存 Knowledge）
4. 可重新计算的结果

## 加载策略

### 注入流程

```
1. Prompt 常驻 MemoryIndex 摘要（最多 20 条）
2. MemorySelector 根据用户问题筛选相关 memory（语义匹配 + 类型匹配）
3. 只加载 topN MemoryDetail（默认 N=5）
4. ContextAssembler 注入 Prompt 的 [DYNAMIC_SELECTED_MEMORY] 区域
```

### Token 预算

- MemoryIndex 摘要：最多 500 tokens
- MemoryDetail 内容：最多 1000 tokens
- 总计：不超过 Context 预算的 15%

## 与旧系统的关系

| 旧组件 | 新定位 |
|--------|--------|
| ManusMemoryStore | 短期对话记忆（ChatMemory），不属于 Memory System |
| FileBasedChatMemory | 短期对话记忆的文件实现 |
| InMemoryChatMemoryRepository | Spring AI 内置，保持不变 |

**重要区分**：
- **ChatMemory**（短期）：当前会话的消息历史，由 Spring AI MessageWindowChatMemory 管理
- **Memory System**（长期）：跨会话的结构化记忆，由 MemoryIndex + MemoryDetail 管理
