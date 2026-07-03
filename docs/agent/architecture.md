# 架构说明

## 整体定位

sxw-ai-agent 是一个 **Java 版 Agent Harness 平台**，核心公式：

```
Agent = Model + Harness
```

Harness 包含模型之外的一切：Prompt、Context、Tool、Memory、Knowledge、Trace、Eval、权限、安全、恢复机制。

## 架构分层

```
┌─────────────────────────────────────────────────────────┐
│                    API 层                                │
│  AgentController · HermesController · KnowledgeController│
├─────────────────────────────────────────────────────────┤
│                  Orchestrator 层                         │
│         AgentOrchestrator · RequestGuard                 │
├─────────────────────────────────────────────────────────┤
│                  Runtime 层                              │
│     ToolUseLoopRuntime · LegacyReActRuntime              │
├─────────────────────────────────────────────────────────┤
│                  Profile 层                              │
│     LoveProfile · GeneralProfile · HermesProfile         │
├─────────────────────────────────────────────────────────┤
│                  基础设施层                               │
│  Prompt · Context · Tool · Memory · Knowledge · Trace    │
├─────────────────────────────────────────────────────────┤
│                  外部服务层                               │
│  DashScope · PgVector · Redis · MCP · RAGFlow(optional) │
└─────────────────────────────────────────────────────────┘
```

## 核心组件

### AgentRuntime

统一执行引擎接口，定义 `execute(AgentContext) -> AgentResponse`。

两种实现：
- **ToolUseLoopRuntime**（新主运行时）：模型返回 `tool_use` 则执行工具并继续循环，返回 `end_turn` 则结束。减少 Thought 文本开销和解析复杂度。
- **LegacyReActRuntime**（旧运行时）：包装现有 SxwManus，保留 ReAct 能力用于兼容。

### AgentProfile

业务配置接口，每个 Profile 定义：
- `systemPrompt()` - 系统提示词
- `enabledToolNames()` - 启用的工具列表
- `knowledgeScopes()` - 知识库范围
- `memoryPolicy()` - 记忆策略
- `toolPolicy()` - 工具策略（风险等级、审批要求）
- `outputPolicy()` - 输出策略

### AgentOrchestrator

统一调度器，负责：
1. 接收 AgentRequest
2. 生成 requestId / traceId
3. 选择 AgentProfile
4. 选择 AgentRuntime
5. 构建 AgentContext
6. 执行并返回 AgentResponse
7. 发布 AgentRunCompletedEvent

### Tool System

工具治理系统，执行链路：

```
ToolCall → ToolRegistry → ParameterValidator → ToolPolicy → ToolRiskEvaluator
  → ApprovalService → ToolExecutor → ToolHookManager → ToolAuditLog
```

风险等级：READ_ONLY / LOCAL_WRITE / EXTERNAL_WRITE / DESTRUCTIVE / SHELL

### Knowledge Base

自研知识库，基于 PgVector：

```
DocumentIngest → MarkdownTextSplitter → EmbeddingService → PgVectorKnowledgeStore
KnowledgeRetriever → topK chunks → Citation 输出
KnowledgeHitRecorder → 命中追踪
```

### Memory System

结构化记忆，区分 Index 和 Detail：

```
MemoryIndex（轻量摘要，常驻 Prompt）
  → MemorySelector（根据问题筛选相关记忆）
  → MemoryDetail（详细内容，按需加载 topN）
  → ContextAssembler（注入 Prompt）
```

记忆类型：USER / FEEDBACK / PROJECT / REFERENCE

### Hermes Agent

Harness 复盘 Agent，不自动写入，只生成 Candidate：

```
Agent 执行完成 → Trace 记录 → AgentRunCompletedEvent
  → HermesAnalyzer 异步分析 → 生成 Candidate
  → 用户审核（approve / reject）→ 应用到对应模块
```

Candidate 类型：MEMORY / KNOWLEDGE / EVAL_CASE / AGENT_RULE / PROMPT_IMPROVEMENT / TOOL_IMPROVEMENT

### Trace & Observability

全链路追踪：

```
ai_request_trace   - 请求级追踪
ai_model_call      - 模型调用记录
ai_tool_call       - 工具调用记录
ai_context_item    - 上下文组件记录
ai_knowledge_hit   - 知识检索命中记录
ai_prompt_run      - Prompt 版本和 hash 记录
```

## 数据流

### 同步请求流

```
HTTP Request
  → RequestGuard（生成 requestId / traceId）
  → AgentOrchestrator.handleRequest()
  → Profile 选择 → Runtime 选择 → Context 构建
  → ToolUseLoopRuntime.execute()
  → [Model Call → Tool Call → Loop]
  → AgentResponse
  → HTTP Response + Trace 记录
```

### 异步事件流

```
AgentRunCompletedEvent
  → HermesAnalyzer（异步）
  → HermesCandidateService（生成 Candidate）
  → HermesReviewService（用户审核）
  → HermesApplier（应用到模块）
```

## 数据库表设计（规划）

| 表名 | 用途 | 阶段 |
|------|------|------|
| ai_request_trace | 请求追踪 | P1 |
| ai_model_call | 模型调用记录 | P2 |
| ai_tool_call | 工具调用审计 | P3 |
| ai_context_item | 上下文组件 | P4 |
| ai_prompt_run | Prompt 版本追踪 | P4 |
| ai_knowledge_document | 知识文档 | P5 |
| ai_knowledge_chunk | 知识分块 | P5 |
| ai_knowledge_hit | 检索命中 | P5 |
| ai_memory_item | 结构化记忆 | P6 |
| ai_hermes_candidate | Hermes 候选 | P8 |
| ai_eval_case | 评测用例 | P9 |
| ai_eval_run | 评测运行 | P9 |

## 与旧架构的映射

| 旧组件 | 新定位 |
|--------|--------|
| LoveApp | LoveProfile（业务能力保留，入口统一到 AgentOrchestrator） |
| SxwManus | GeneralProfile + LegacyReActRuntime |
| HermesAgent | HermesProfile（对话）+ HermesAnalyzer（复盘） |
| BaseAgent / ReActAgent / ToolCallAgent | LegacyReActRuntime 内部实现 |
| ManusMemoryStore | MemorySystem 的内存实现（后续升级） |
| ToolRegistration | ToolRegistry 的基础（后续加入治理） |
