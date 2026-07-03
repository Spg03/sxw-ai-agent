# SXW AI Agent - Harness 平台指南

## 项目定位

基于 Spring Boot 3、Spring AI Alibaba、PgVector 和 MCP 的**企业级 Agent Harness 平台**。

核心理念：**Agent = Model + Harness**

项目聚焦于模型之外的系统化工程：Prompt、Context、Tool、Memory、Knowledge、Trace、Eval、权限、安全、恢复机制，使其更稳定、更可控、更可回放。

## 核心架构

```
Agent Runtime（统一执行引擎）
├── ToolUseLoopRuntime（新主运行时，Tool-Use Loop 模式）
└── LegacyReActRuntime（旧运行时，兼容 ReAct 模式）

Agent Profile（业务配置）
├── LoveProfile（恋爱咨询专家）
├── GeneralProfile（通用任务助手）
└── HermesProfile（情感陪伴伙伴）

Tool System（工具治理）
├── ToolDefinition（工具定义 + 风险等级）
├── ToolExecutor（统一执行）
├── ToolRiskEvaluator（风险评估）
└── ToolAuditLog（审计追踪）

Knowledge Base（自研知识库）
├── PgVectorKnowledgeStore（向量存储）
├── KnowledgeRetriever（检索服务）
└── KnowledgeHitRecorder（命中追踪）

Memory System（结构化记忆）
├── MemoryIndex（轻量索引，常驻 Prompt）
├── MemoryDetail（详细内容，按需加载）
└── MemorySelector（相关记忆筛选）

Hermes Agent（Harness 复盘）
├── Trace 分析器
├── Candidate 生成器（Memory / Knowledge / Eval / Rule）
└── 审核 -> 应用闭环
```

## 详细规则文档

以下文档包含各子系统的详细设计与规则：

- [架构说明](docs/agent/architecture.md) - 整体架构、组件职责、数据流
- [工具规则](docs/agent/tool-rules.md) - 工具注册、风险等级、审批规则、审计要求
- [记忆规则](docs/agent/memory-rules.md) - Memory 分类、写入规则、加载策略、审核机制
- [上下文规则](docs/agent/context-rules.md) - Context 分区、Token 预算、压缩策略
- [Hermes 规则](docs/agent/hermes-rules.md) - Hermes 职责、Candidate 生成规则、审核流程
- [失败案例](docs/agent/failure-cases.md) - 常见失败模式、恢复策略、经验教训

## 开发指南

### 新增工具

1. 创建 Tool 类，实现工具逻辑
2. 定义 `ToolDefinition`，标注风险等级（READ_ONLY / LOCAL_WRITE / EXTERNAL_WRITE / DESTRUCTIVE / SHELL）
3. 在 `ToolRegistration` 中注册
4. 高风险工具（DESTRUCTIVE / SHELL）默认关闭，需审批
5. 专用工具优先于 ShellTool，避免滥用 Bash

### 新增记忆

1. 记忆不能自动写入，必须通过 Hermes Candidate 审核
2. 记忆类型：USER（用户偏好）、FEEDBACK（反馈规则）、PROJECT（项目状态）、REFERENCE（引用指针）
3. Memory Index 常驻 Prompt，Memory Detail 按需加载
4. 每条记忆包含：name、description、type、confidence、status

### Prompt 修改

1. Prompt 必须记录版本号和 hash
2. 使用 PromptAssembler 动态组装 Section，不要硬编码
3. 静态 Section（角色、安全红线）与动态 Section（上下文、历史）分离
4. 每次模型调用记录 promptCode、promptVersion、staticPromptHash、dynamicPromptHash

### 工具调用

1. 所有工具调用必须写入 `ai_tool_call` 审计日志
2. 工具失败能被 Hermes 分析，生成改进候选
3. READ_ONLY 工具可直接执行，DESTRUCTIVE / SHELL 工具必须审批
4. 工具执行链路：ToolCall → Registry → Validator → Policy → RiskEvaluator → Approval → Executor → Hook → Audit

## 设计原则

1. **渐进式改造**：保留 LegacyReActRuntime，新增 ToolUseLoopRuntime，风险可控
2. **向后兼容**：原接口不破坏，新增统一入口 `/api/agent/chat`
3. **可观测性**：所有请求生成 requestId / traceId，Trace 可回放
4. **可治理性**：Tool、Memory、Knowledge 全部纳入审核机制
5. **可持续演进**：Hermes 分析 Trace，生成候选，形成"执行→观测→复盘→审核→沉淀→评测"闭环

## 快速开始

### 统一 API 入口

```http
POST /api/agent/chat
Content-Type: application/json

{
  "chatId": "chat_001",
  "profile": "GENERAL",
  "message": "帮我分析这个项目的架构",
  "stream": true
}
```

### 支持的 Profile

- `LOVE` - 恋爱咨询专家（只读工具）
- `GENERAL` - 通用任务助手（读写工具）
- `HERMES` - 情感陪伴伙伴（无工具）

### 运行模式

- `CHAT` - 普通问答
- `PLAN` - 计划模式（只读工具，用户确认后再执行）
- `EXECUTE` - 执行模式（用户确认后的写操作）

## 技术栈

- **框架**：Spring Boot 3.4.4 + Java 21
- **AI 框架**：Spring AI 1.0.0 + Spring AI Alibaba 1.0.0.2
- **LLM**：DashScope（通义千问 qwen-plus）
- **向量存储**：PgVector（PostgreSQL）
- **数据库**：PostgreSQL + MySQL + Flyway
- **缓存**：Caffeine + Redis
- **安全**：Spring Security + JWT + API Key
- **协议**：MCP（Model Context Protocol）
- **监控**：Micrometer + Prometheus + Agent Trace
- **文档**：Knife4j (OpenAPI 3)

## 参考资源

- [JavaGuide - Agent Harness 工程化](https://javaguide.cn/ai/agent/harness-engineering.html)
- [小林面试笔记 - Claude Code 源码分析](https://xiaolinnote.com/claudecode/source/cc_source.html)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba 文档](https://sca.aliyun.com/docs/ai/)
