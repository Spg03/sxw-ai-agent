# 上下文规则

## 概述

上下文（Context）是每次模型调用时注入的完整信息集合。上下文不是越多越好，超过一定利用率后 Agent 质量可能下降。必须对上下文进行分区管理、预算控制和压缩策略。

## Context 分区

每次模型调用的 Prompt 按以下顺序组装：

```
┌─────────────────────────────────────────┐
│ [STATIC] 静态区域（变化频率低）           │
│                                         │
│ 1. System Rules（角色、安全红线）         │
│ 2. Tool Rules（工具使用规则）            │
│ 3. Output Rules（输出格式要求）          │
├─────────────────────────────────────────┤
│ [DYNAMIC] 动态区域（每次调用变化）        │
│                                         │
│ 4. Profile Config（当前 Profile 配置）   │
│ 5. Memory Index（记忆摘要列表）          │
│ 6. Selected Memory（选中的记忆详情）      │
│ 7. Knowledge Evidence（知识库检索结果）   │
│ 8. Tool Results（工具执行结果）          │
│ 9. Conversation History（对话历史）      │
│ 10. Current User Message（当前用户消息）  │
└─────────────────────────────────────────┘
```

## Token 预算

### 预算分配

```java
public class ContextBudget {
    private int maxInputTokens;       // 模型最大输入 token（如 8192）
    private int reservedOutputTokens; // 预留输出 token（如 2048）
    private int availableTokens;      // 可用 token = maxInput - reservedOutput
    
    // 各区域预算分配
    private int staticBudget;         // 静态区域：30%
    private int memoryBudget;         // 记忆区域：15%
    private int knowledgeBudget;      // 知识区域：20%
    private int toolResultBudget;     // 工具结果：20%
    private int historyBudget;        // 对话历史：15%
}
```

### 默认预算（以 qwen-plus 8K 为例）

| 区域 | 比例 | Token 数 |
|------|------|----------|
| 静态（System + Rules） | 30% | ~1800 |
| 记忆（Index + Detail） | 15% | ~900 |
| 知识（Knowledge Evidence） | 20% | ~1200 |
| 工具（Tool Results） | 20% | ~1200 |
| 历史（Conversation） | 15% | ~900 |
| **总计** | **100%** | **~6000** |

### 超预算处理

当某个区域超出预算时，按优先级降级：

```
1. 对话历史：裁剪最早的消息
2. 工具结果：大结果只保留 preview，全文落库
3. 知识证据：减少 topK 数量
4. 记忆详情：减少加载条数
5. 静态区域：不降级（最后才压缩）
```

## 压缩策略

### 三层压缩（第一版）

```
第一层：大 Tool Result 压缩
  - 结果超过 500 字符时，只保留 preview（前 200 字符 + "...已截断"）
  - 完整结果存入 ai_context_item 表

第二层：旧历史消息压缩
  - 超过 10 轮的历史消息，压缩成 summary
  - summary 格式："前 N 轮讨论了 [主题1]、[主题2]，结论是 [...]"

第三层：可重新获取的结果只保留引用
  - WebSearch 结果只保留 URL 和摘要
  - Knowledge 结果只保留 chunk_id 和摘要
```

### 压缩后恢复（Post-Compact Restoration）

压缩后需要恢复关键内容：

```
1. 最近命中的 KnowledgeChunk（引用 + 摘要）
2. 最近成功 ToolResult 摘要
3. 当前 Plan（如果在 Plan Mode）
4. 用户确认过的 Memory
5. 当前任务目标
```

## Context 记录

每次模型调用的 Context 组件都记录到 `ai_context_item`：

```sql
CREATE TABLE ai_context_item (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    turn INT NOT NULL,
    section VARCHAR(64) NOT NULL,        -- STATIC_RULES / MEMORY_INDEX / KNOWLEDGE / TOOL_RESULT / HISTORY
    content_type VARCHAR(64),            -- text / json / summary
    content_preview TEXT,                -- 内容预览（前 500 字符）
    content_full TEXT,                   -- 完整内容
    token_count INT,                     -- token 数量
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Prompt 版本管理

```sql
CREATE TABLE ai_prompt_run (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    prompt_code VARCHAR(64) NOT NULL,    -- Profile 标识
    prompt_version VARCHAR(32) NOT NULL, -- 版本号
    static_prompt_hash VARCHAR(64),      -- 静态部分 hash
    dynamic_prompt_hash VARCHAR(64),     -- 动态部分 hash
    rendered_prompt_hash VARCHAR(64),    -- 完整渲染 hash
    total_tokens INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 不要做的事

1. 不要把整个知识库塞进上下文
2. 不要忽略 token 预算，让模型自己截断
3. 不要在上下文里放原始 HTML / JSON 大对象
4. 不要把工具结果和历史消息混在一起
5. 不要在静态区域放动态内容
