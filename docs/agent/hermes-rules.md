# Hermes 规则

## 概述

Hermes 不是普通聊天助手，而是 **Harness 复盘 Agent**。它的职责是分析 Agent 的运行轨迹，生成改进候选，形成持续演进闭环。

## 双重身份

| 身份 | 职责 | 实现 |
|------|------|------|
| **HermesProfile** | 情感陪伴对话 | 现有 HermesAgent |
| **HermesAnalyzer** | Harness 复盘分析 | 新增异步分析器 |

## Hermes 复盘能力

Hermes 分析器负责：

1. **Trace 分析** - 分析 Agent 运行轨迹，识别成功模式和失败模式
2. **Memory 候选生成** - 从对话中提取用户偏好、反馈规则
3. **Knowledge 候选生成** - 识别缺失的知识，建议补充文档
4. **Eval Case 生成** - 从成功/失败案例中生成评测用例
5. **Prompt 优化建议** - 分析 Prompt 效果，建议改进方向
6. **Tool 改进建议** - 分析工具调用模式，建议工具优化

## Candidate 生成规则

### Candidate 类型

```java
public enum HermesCandidateType {
    MEMORY,              // 记忆候选（用户偏好、反馈规则）
    KNOWLEDGE,           // 知识候选（缺失文档、FAQ）
    EVAL_CASE,           // 评测用例（输入输出对）
    AGENT_RULE,          // Agent 规则（行为约束）
    PROMPT_IMPROVEMENT,  // Prompt 优化建议
    TOOL_IMPROVEMENT,    // 工具改进建议
    DOC_UPDATE           // 文档更新建议
}
```

### Candidate 状态

```java
public enum HermesCandidateStatus {
    PENDING,     // 待审核
    APPROVED,    // 已批准
    REJECTED,    // 已拒绝
    APPLIED,     // 已应用
    FAILED       // 应用失败
}
```

### Candidate 表

```sql
CREATE TABLE ai_hermes_candidate (
    id BIGSERIAL PRIMARY KEY,
    candidate_id VARCHAR(64) NOT NULL UNIQUE,
    source_request_id VARCHAR(64) NOT NULL,
    source_trace_id VARCHAR(64) NOT NULL,
    candidate_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_store VARCHAR(64) NOT NULL,    -- memory / knowledge / eval / prompt / tool / doc
    confidence DECIMAL(5,4),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(64),
    applied_at TIMESTAMP,
    apply_result TEXT
);
```

## 执行流程

```
1. Agent 执行完成
2. 记录完整 Trace（model calls + tool calls）
3. 发布 AgentRunCompletedEvent
4. HermesAnalyzer 异步分析 Trace
5. 生成 Candidate（多个类型）
6. 写入 ai_hermes_candidate（status = PENDING）
7. 用户通过 API 审核（approve / reject）
8. 审核通过后，HermesApplier 应用到对应模块
9. 更新 status = APPLIED
```

## Candidate API

```http
# 查看待审核候选
GET /api/hermes/candidates?status=PENDING

# 批准候选
POST /api/hermes/candidates/{id}/approve

# 拒绝候选
POST /api/hermes/candidates/{id}/reject

# 应用已批准的候选
POST /api/hermes/candidates/{id}/apply
```

## 审核规则

### 自动审核

以下情况可自动批准（confidence >= 0.9）：
- 用户明确表达的偏好（"我喜欢简洁回答"）
- 工具调用成功模式的总结
- 重复出现的知识缺失

### 人工审核

以下情况必须人工审核：
- 涉及安全规则的变更
- 系统 Prompt 修改建议
- 工具行为变更
- 置信度低于 0.9 的候选

## Hermes 限制

### 核心原则：Hermes 只生成候选，不自动污染系统

1. **不自动写入 Memory** - 所有记忆候选必须审核
2. **不自动修改 Prompt** - Prompt 建议必须审核
3. **不自动添加工具** - 工具改进必须审核
4. **不自动执行操作** - Hermes 是分析器，不是执行器
5. **不影响当前会话** - Hermes 分析是异步的，不阻塞主流程

## 分析维度

Hermes 分析 Trace 时关注：

| 维度 | 分析内容 | 产出 |
|------|----------|------|
| 成功模式 | 哪些工具组合效果好 | MEMORY / KNOWLEDGE |
| 失败模式 | 哪些工具调用失败 | TOOL_IMPROVEMENT |
| 用户偏好 | 用户喜欢什么风格的回答 | MEMORY (USER) |
| 知识缺口 | 哪些检索没有命中 | KNOWLEDGE |
| Prompt 效果 | 哪些 Prompt 版本效果好 | PROMPT_IMPROVEMENT |
| 效率问题 | 哪些调用链过长 | AGENT_RULE |
