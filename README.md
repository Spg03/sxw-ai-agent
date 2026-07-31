<p align="center">
  <h1 align="center">🤖 sxw-ai-agent</h1>
  <p align="center">
    <strong>企业级 AI Agent 工程化平台 — Agent = Model + Harness</strong>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
    <img src="https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen" alt="Spring Boot 3.4.4"/>
    <img src="https://img.shields.io/badge/Spring%20AI-1.0.0-blue" alt="Spring AI 1.0.0"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey" alt="License"/>
  </p>
  <p align="center">
    <a href="#快速启动">🚀 快速启动</a> •
    <a href="#核心架构">🏗️ 架构</a> •
    <a href="#核心能力">✨ 能力</a> •
    <a href="#api-一览">📡 API</a> •
    <a href="#部署">🐳 部署</a>
  </p>
</p>

---

## 为什么选择这个项目？

这不只是一个「聊天 Demo」，而是一个 **围绕 Agent Harness 工程化的完整后端系统**。

当大多数 AI 项目停留在「调 API + 拼 Prompt」时，本项目聚焦于模型之外的系统化工程——

> Prompt 管理 · Context 预算 · Tool 治理 · Memory 审核 · Knowledge 检索 · Trace 回放 · Eval 评测 · 主备降级 · 限流熔断 · JWT 鉴权 · MCP 协议

一句话：**让 AI Agent 真正跑在生产环境里。**

---

## 核心架构

![Uploading image.png…]()


### 双运行时架构

项目支持两种 Agent 执行引擎，由 `AgentOrchestrator` 统一调度：

| 运行时 | 模式 | 适用场景 |
|--------|------|---------|
| `ToolUseLoopRuntime` | Tool-Use Loop | GENERAL 通用任务（新架构，支持工具治理 + 风险审批） |
| `LegacyReActRuntime` | ReAct | LOVE / HERMES 情感场景（兼容旧链路） |

### ChatModel 主备降级

```
请求 ──► FallbackChatModel (@Primary)
              │
              ├── try  ──► DashScope (qwen-plus)
              │
              └── catch ──► Ollama (本地模型，离线可用)
```

所有消费者自动获得降级能力，无需修改业务代码。同步调用和流式调用均支持自动切换。

---

## 核心能力

### 🧠 多 Agent 协同

| Agent | Profile | 描述 |
|-------|---------|------|
| **ClassicAgent** | — | 传统工具型 Agent，支持聊天、RAG、工具调用和 MCP |
| **LoveApp** | LOVE | 恋爱咨询领域专家，集成 RAG 知识库 + 结构化输出 |
| **Hermes** | HERMES | 私人树洞陪伴 Agent，情绪识别 + 温暖回应 + 持久化记录 |
| **SxwManus** | GENERAL | 通用 ReAct Agent，支持工具调用、技能加载和运行轨迹 |

### 🔧 工具治理体系

不是简单的「给 Agent 一堆工具」，而是完整的治理闭环：

```
ToolCall → Registry → Validator → Policy → RiskEvaluator → Approval → Executor → Hook → Audit
```

- **5 级风险等级**：`READ_ONLY` / `LOCAL_WRITE` / `EXTERNAL_WRITE` / `DESTRUCTIVE` / `SHELL`
- **审批机制**：高风险工具（DESTRUCTIVE / SHELL）默认关闭，需显式审批
- **审计追踪**：所有工具调用写入审计日志，可回放、可分析
- **终端沙箱**：白名单命令限制，防止 RCE

### 📚 知识库检索（RAG）

- **自建向量检索**：PgVector 存储 + 文档切分 + Embedding + 语义检索
- **RAGFlow 集成**：可接入外部知识库服务，支持混合检索
- **查询重写**：`QueryRewriter` 优化用户提问的检索效果
- **命中追踪**：`KnowledgeHitTracker` 记录检索命中率，持续优化

### 🧩 结构化记忆

```
MemoryIndex（轻量索引，常驻 Prompt）
     │
     ▼ 按需加载
MemoryDetail（详细内容）
     │
     ▼ Hermes 审核
写入 / 更新 / 归档
```

- **4 种记忆类型**：USER（用户偏好）/ FEEDBACK（反馈规则）/ PROJECT（项目状态）/ REFERENCE（引用指针）
- **审核写入**：记忆不能自动写入，必须通过 Hermes Candidate 审核流程

### 📊 可观测性

- **Agent Trace**：每次执行记录 traceId、阶段、工具调用、输入输出、耗时
- **Trace 回放**：前端可视化 Agent 思考过程，支持逐步回放
- **Prometheus 指标**：LLM 延迟、Token 消耗、缓存命中率、Agent 步骤数
- **Actuator 端点**：健康检查、指标暴露、熔断状态

### 🔐 多层安全

- **JWT 鉴权**：用户注册/登录，Bearer Token 无状态认证
- **API Key 拦截**：可选的接口级保护，适合公网演示
- **Spring Security**：BCrypt 密码加密、CSRF 防护、会话管理
- **工具权限**：基于风险等级的工具访问控制

### ⚡ 高可用三件套

基于 Resilience4j 的 LLM 调用保护：

| 策略 | 配置 |
|------|------|
| **重试** | 最多 3 次，指数退避 |
| **限流** | 30 req/s，超限立即失败 |
| **熔断** | 滑动窗口 20 次，50% 失败率触发，30s 半开恢复 |

### 🗣️ MCP 协议

- **MCP Server**：将内部 Skill 以 SSE 协议暴露给 Claude Desktop、Cursor 等外部 Agent
- **MCP Client**：可对接外部 MCP 服务，扩展 Agent 能力边界

---

## 技术栈

| 领域 | 技术选型 |
|------|---------|
| 语言 & 运行时 | Java 21 |
| 框架 | Spring Boot 3.4.4 |
| AI 框架 | Spring AI 1.0.0 + Spring AI Alibaba 1.0.0.2 |
| LLM 提供商 | 阿里云 DashScope（通义千问）+ Ollama（本地） |
| 向量存储 | PgVector (PostgreSQL) |
| 数据库 | PostgreSQL + MySQL（H2 测试） |
| 缓存 | Caffeine（本地 LRU）+ Redis（分布式） |
| 安全 | Spring Security + JWT + API Key + BCrypt |
| 协议 | MCP (Model Context Protocol) SSE |
| 高可用 | Resilience4j（Retry / RateLimiter / CircuitBreaker） |
| 监控 | Micrometer + Prometheus + Agent Trace |
| 文档 | Knife4j (OpenAPI 3) |
| 部署 | Docker + Docker Compose |
| 辅助 | LangChain4J DashScope, jsoup, iText PDF, Hutool, Kryo |

---

## 项目结构

```
src/main/java/com/sxw/sxwaiagent/
├── agent/                    # Agent 核心
│   ├── orchestrator/         #   统一调度器（双运行时切换）
│   ├── runtime/              #   执行引擎（ToolUseLoop + LegacyReAct）
│   ├── profile/              #   业务配置（Love / General / Hermes）
│   ├── prompt/               #   Prompt 组装 & 版本管理
│   ├── tool/                 #   工具治理（注册、风险、审批、审计）
│   ├── trace/                #   运行轨迹记录
│   ├── hermes/               #   Hermes 情感陪伴 Agent
│   ├── classic/              #   Classic 传统 Agent
│   └── dto/                  #   请求/响应模型
├── auth/                     # JWT 认证 & 用户管理
├── common/                   # 通用基础设施
│   ├── config/               #   ChatModel 主备降级、CORS、Executor
│   ├── security/             #   API Key 拦截器
│   └── api/                  #   统一响应 & 全局异常
├── context/                  # Context 预算管理 & 压缩
├── hermes/                   # Hermes 复盘系统（Trace 分析 → Candidate → 审核 → 应用）
├── infrastructure/           # 基础设施层
│   ├── advisor/              #   Logger Advisor、Re-Reading Advisor
│   ├── cache/                #   LLM 应答缓存（Caffeine + Redis）
│   ├── eval/                 #   评测框架（关键词 + LLM-Judge）
│   ├── memory/               #   会话记忆 & Manus 记忆存储
│   ├── rag/                  #   RAG 全链路（文档加载、向量存储、RAGFlow）
│   ├── resilience/           #   DashScope 限流熔断 Advisor
│   ├── skill/                #   技能注册 & MCP 暴露
│   ├── tools/                #   工具实现（终端、文件、PDF、Web、RAGFlow）
│   └── trace/                #   Agent Trace 存储
├── knowledge/                # 自建知识库（检索、切分、命中追踪）
├── love/                     # LoveApp 恋爱咨询 Agent
├── manus/                    # SxwManus ReAct Agent（Base → ReAct → ToolCall）
├── memory/                   # 结构化记忆系统
├── plan/                     # 计划模式（Plan / Execute）
├── trace/                    # Trace 回放服务
├── treehole/                 # 树洞（情绪日记 + Hermes 陪伴）
└── web/controller/           # REST API 层
```

---

## 快速启动

### 环境要求

- JDK 21
- Maven 3.9+
- DashScope API Key（[申请地址](https://dashscope.console.aliyun.com/)）
- 可选：本地 Ollama（离线降级）、PostgreSQL + PgVector、Redis、RAGFlow

### 1. 克隆 & 配置

```bash
git clone https://github.com/Spg03/sxw-ai-agent.git
cd sxw-ai-agent
cp .env.example .env
```

编辑 `.env`，至少配置：

```bash
DASHSCOPE_API_KEY=sk-xxx
DASHSCOPE_CHAT_MODEL=qwen-plus
```

### 2. 启动

```bash
mvn spring-boot:run
```

### 3. 访问

| 地址 | 说明 |
|------|------|
| http://localhost:8123/api/v1/ | 前端页面 |
| http://localhost:8123/api/v1/health | 健康检查 |
| http://localhost:8123/api/v1/doc.html | API 文档 (Knife4j) |
| http://localhost:8123/api/v1/actuator/health | Actuator 健康 |
| http://localhost:8123/api/v1/actuator/prometheus | Prometheus 指标 |
| http://localhost:8123/api/v1/sse | MCP SSE 端点 |

---

## API 一览

### 用户认证

```http
POST /api/v1/auth/register      # 注册
POST /api/v1/auth/login         # 登录（返回 JWT）
GET  /api/v1/auth/me            # 当前用户信息
```

### Agent 对话

```http
# 统一入口（Orchestrator 调度）
POST /api/v1/agent/chat
Content-Type: application/json

{
  "chatId": "chat_001",
  "profile": "GENERAL",
  "message": "帮我分析这段代码",
  "stream": true
}
```

| Profile | 说明 | 运行时 |
|---------|------|--------|
| `LOVE` | 恋爱咨询专家 | LegacyReAct |
| `GENERAL` | 通用任务助手 | ToolUseLoop |
| `HERMES` | 情感陪伴伙伴 | LegacyReAct |

### Classic & Hermes Agent

```http
GET /api/v1/agents/classic/chat?message=...&chatId=...
GET /api/v1/agents/hermes/chat?message=...&chatId=...
```

### 树洞（情感日记）

```http
POST /api/v1/treeholes           # 创建树洞（Hermes 自动回应）
GET  /api/v1/treeholes           # 我的树洞列表
GET  /api/v1/treeholes/{id}      # 查看详情
DELETE /api/v1/treeholes/{id}   # 删除
```

### 运行轨迹

```http
GET /api/v1/agent/traces/{chatId}?limit=5    # 查看 Agent 执行轨迹
```

### 知识库

```http
GET /api/v1/ai/love_app/chat/ragflow/sync?message=...&chatId=...
```

---

## 配置参考

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | — | 阿里云 DashScope API Key |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | DashScope 模型名称 |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 地址（降级备选） |
| `OLLAMA_CHAT_MODEL` | `gemma3:1b` | Ollama 模型名称 |
| `MCP_SERVER_ENABLED` | `true` | 是否启用 MCP Server |
| `TERMINAL_TOOL_ENABLED` | `false` | 是否启用终端工具 |
| `SXW_SECURITY_API_KEY_ENABLED` | `false` | 是否启用 API Key 保护 |
| `SXW_SECURITY_API_KEY` | — | API Key 值 |
| `RAGFLOW_ENABLED` | `false` | 是否启用 RAGFlow 检索 |
| `RAGFLOW_BASE_URL` | `http://localhost:9380` | RAGFlow 地址 |
| `LLM_CACHE_ENABLED` | `true` | LLM 应答缓存开关 |
| `LLM_CACHE_MAX_SIZE` | `1000` | 缓存最大条数 |
| `LLM_CACHE_TTL_MINUTES` | `30` | 缓存过期时间 |

### RAGFlow 接入

```bash
RAGFLOW_ENABLED=true
RAGFLOW_BASE_URL=http://localhost:9380
RAGFLOW_API_KEY=<your-ragflow-api-key>
RAGFLOW_DATASET_IDS=<dataset-id-1,dataset-id-2>
```

### API Key 保护

公网部署时建议开启：

```bash
SXW_SECURITY_API_KEY_ENABLED=true
SXW_SECURITY_API_KEY=<strong-random-value>
```

受保护路径：`/api/v1/ai/**`、`/api/v1/notes/**`、`/api/v1/skills/**`、`/api/v1/agent/**`

请求头：
```http
X-SXW-API-Key: ***
```

---

## 部署

### Docker Compose

```bash
docker compose --env-file .env up -d --build
```

查看日志：

```bash
docker compose logs -f sxw-ai-agent
```

### 生产建议

- 不要将真实 Key 写入代码仓库，通过环境变量或密钥管理系统注入
- 开启 API Key 保护 + JWT 鉴权
- 配置 PgVector 持久化向量存储
- 配置 Redis 分布式缓存
- 启用 Resilience4j 熔断保护
- 日志脱敏、防火墙限制

---

## 测试

```bash
# 普通单测（不消耗 LLM Token）
mvn test

# 完整验证
mvn verify

# 评测套件（手动触发，消耗 LLM Token）
mvn test -Dgroups=eval -Dtest=LoveAppEvalSuiteTest
```

> 评测默认排除在 `mvn test` 之外，避免 CI 环境误消耗 Token。

---

## 简历话术

> 设计并实现了基于 Spring AI 的企业级 AI Agent 工程化平台。采用「Agent = Model + Harness」理念，实现双运行时引擎（ToolUseLoop + LegacyReAct）、ChatModel 主备降级（DashScope + Ollama）、工具 5 级风险治理、结构化记忆审核、PgVector 知识库检索、Agent Trace 回放、LLM-Judge 评测、JWT + API Key 多层鉴权、Resilience4j 高可用三件套、MCP 协议暴露和 Prometheus 可观测性。项目覆盖 AI Agent 从开发到运维的完整工程链路。

---

## 后续规划

- [ ] 将 Agent Trace、会话记忆和评测报告持久化到数据库
- [ ] 前端工程独立化，增加 Agent 可视化面板和可观测 Dashboard
- [ ] 补充 GitHub Actions CI/CD 和 Docker 镜像发布
- [ ] 接入更多 LLM Provider（OpenAI、DeepSeek 等）
- [ ] Hermes 复盘系统自动化闭环（Trace → 问题发现 → 改进建议 → 人工审核 → 应用）
- [ ] 线上演示环境搭建

---

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

<p align="center">
  <strong>如果这个项目对你有帮助，请给一个 ⭐ Star 支持！</strong>
</p>
