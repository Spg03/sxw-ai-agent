# 工具规则

## 概述

工具系统是 Agent 与外部世界交互的桥梁。每个工具都必须有明确的定义、风险等级、权限要求和审计记录。

## 工具定义

每个工具必须定义 `ToolDefinition`：

```java
public record ToolDefinition(
    String name,                    // 工具名称（唯一标识）
    String description,             // 工具描述
    ToolRiskLevel riskLevel,        // 风险等级
    boolean readOnly,               // 是否只读
    boolean destructive,            // 是否有破坏性
    boolean concurrencySafe,        // 是否并发安全
    boolean requiresApproval,       // 是否需要审批
    List<AgentProfileCode> enabledProfiles  // 启用的 Profile
) {}
```

## 风险等级

```java
public enum ToolRiskLevel {
    READ_ONLY,          // 只读操作，无副作用（KnowledgeSearch, WebSearch）
    LOCAL_WRITE,        // 本地写操作（FileOperation, NoteSkill）
    EXTERNAL_WRITE,     // 外部写操作（API 调用、邮件发送）
    DESTRUCTIVE,        // 破坏性操作（删除文件、清空数据）
    SHELL               // Shell 命令执行（TerminalOperation）
}
```

## 工具分类

### READ_ONLY 工具（直接执行）

| 工具 | 说明 |
|------|------|
| KnowledgeSearchTool | 知识库检索 |
| MemoryReadTool | 记忆读取 |
| WebSearchTool | 网络搜索 |
| WebScrapingTool | 网页抓取 |
| SkillTool | 技能加载 |
| TraceQueryTool | 追踪查询 |
| RagFlowSearchTool | RAGFlow 检索 |

### LOCAL_WRITE 工具（记录审计）

| 工具 | 说明 |
|------|------|
| FileOperationTool | 文件操作（沙箱内） |
| NoteSkill | 笔记技能 |
| PDFGenerationTool | PDF 生成 |
| ResourceDownloadTool | 资源下载 |

### SHELL / DESTRUCTIVE 工具（需审批，默认关闭）

| 工具 | 说明 |
|------|------|
| TerminalOperationTool | 终端操作 |
| TerminateTool | 终止执行 |

## 工具执行链路

```
1. ToolCall（模型返回工具调用请求）
2. ToolRegistry（查找工具定义）
3. ParameterValidator（参数校验）
4. ToolPolicy（检查 Profile 工具策略）
5. ToolRiskEvaluator（风险评估）
6. ApprovalService（高风险工具需审批）
7. ToolExecutor（执行工具）
8. ToolHookManager（前置/后置钩子）
9. ToolAuditLog（审计日志）
```

## 工具审批规则

| 风险等级 | 默认行为 | 审批要求 |
|----------|----------|----------|
| READ_ONLY | 直接执行 | 无 |
| LOCAL_WRITE | 执行 + 审计 | 无（记录日志） |
| EXTERNAL_WRITE | 需审批 | 用户确认 |
| DESTRUCTIVE | 默认关闭 | 用户确认 + 二次验证 |
| SHELL | 默认关闭 | 用户确认 + 命令白名单 |

## 工具注册规范

1. **专用工具优先**：优先使用专用工具（如 WebSearchTool），而不是通过 ShellTool 调用 curl
2. **单一职责**：每个工具只做一件事
3. **参数校验**：工具必须在执行前校验参数
4. **错误处理**：工具失败时返回明确的错误信息，不吞异常
5. **沙箱约束**：文件操作限制在沙箱目录内

## 工具审计日志

所有工具调用必须记录到 `ai_tool_call`：

```sql
CREATE TABLE ai_tool_call (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    turn INT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    tool_args TEXT,
    tool_result TEXT,
    risk_level VARCHAR(32),
    status VARCHAR(32) NOT NULL,   -- success / failed / rejected / timeout
    latency_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Profile 工具策略

每个 Profile 定义允许的最高风险等级：

| Profile | 最高风险等级 | 说明 |
|---------|-------------|------|
| LoveProfile | READ_ONLY | 只允许检索类工具 |
| GeneralProfile | LOCAL_WRITE | 允许本地写操作 |
| HermesProfile | READ_ONLY（无工具） | 纯对话，不调用工具 |

## 不要做的事

1. 不要让 ShellTool 成为万能工具
2. 不要跳过风险检查直接执行工具
3. 不要在工具执行失败时吞异常
4. 不要让工具修改系统 Prompt 或核心配置
5. 不要在工具中执行其他工具（禁止嵌套调用）
