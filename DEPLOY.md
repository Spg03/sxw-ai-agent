# sxw-ai-agent 运行与部署

## API Key 总览

| 变量 | 必填 | 用途 | 申请位置 |
|---|---|---|---|
| `DASHSCOPE_API_KEY` | 是 | DashScope/百炼 Chat + Embedding + 云知识库 RAG | 阿里云百炼控制台 → API-KEY 管理 |
| `SEARCH_API_KEY` | 否 | `WebSearchTool` 联网搜索（searchapi.io） | searchapi.io 注册 |
| `AMAP_MAPS_API_KEY` | 否 | 高德地图 MCP（仅当配置 amap stdio MCP 客户端时） | console.amap.com → Web 服务 Key |
| Postgres 账号 | 否 | 启用 PgVector 时（默认禁用） | 自建 PG + pgvector 扩展 |

**最小可跑配置**：仅设置 `DASHSCOPE_API_KEY` 即可启动。`NoteSkill` / MCP 服务端 / `/api/notes` REST 端点完全不依赖任何 key，可立即冒烟。

## 环境要求
- JDK 21（`pom.xml` 的 `java.version=21`；用 17 会报“不支持发行版本 21”）
- Maven 3.9+
- 可选：Docker 24+、Docker Compose v2

## 本地运行
1. 复制环境变量模板并填写：
   ```powershell
   Copy-Item .env.example .env
   # 编辑 .env 填入 DASHSCOPE_API_KEY 等
   ```
2. 以本地 profile 启动：
   ```powershell
   $env:DASHSCOPE_API_KEY="sk-xxx"
   mvn spring-boot:run
   ```
3. 验证：
   - 健康检查：`http://localhost:8123/api/health` 或 `http://localhost:8123/api/actuator/health`
   - Knife4j 文档：`http://localhost:8123/api/doc.html`
   - MCP SSE 端点：`http://localhost:8123/api/sse`
   - Prometheus 指标：`http://localhost:8123/api/actuator/prometheus`
     - `ai_chat_latency_seconds_*`：LLM 调用延迟（含分位数）
     - `ai_chat_tokens_total{kind=prompt|completion|total}`：token 用量
     - `ai_chat_calls_total{outcome=ok|error}`：成功/失败计数
     - `executor_*{name="agentTaskExecutor"}`：Agent 线程池队列深度 / 活跃线程
     - `ai_chat_cache_total{outcome=hit|miss-stored|miss-skipped}`：LLM 应答缓存命中率
     - `cache_*{cache="llmAnswerCache"}`：Caffeine 命中数 / 加载耗时 / 淘汰数
     - `resilience4j_retry_calls_total{name="dashscope"}`：重试事件分布
     - `resilience4j_ratelimiter_*{name="dashscope"}`：可用令牌 / 等待线程
     - `resilience4j_circuitbreaker_state{name="dashscope"}`：CB 状态（含 Actuator health 指示器）

## Docker 部署
```powershell
docker compose --env-file .env up -d --build
```
停止：
```powershell
docker compose down
```

## MCP 服务端（本项目作为 MCP Server）
本项目通过 `spring-ai-starter-mcp-server-webmvc` 将带有 `@Tool` 注解的 Skill 暴露为 MCP 工具。
- 传输：SSE over HTTP
- SSE 端点：`GET /api/sse`
- 消息端点：`POST /api/mcp/message`
- 已注册技能：`NoteSkill`（创建/追加/读取/列出/删除 Markdown 笔记）

### 在 Claude Desktop / Cursor 等客户端接入
在 MCP 客户端配置中添加一个 SSE 类型的服务器，URL 指向：
```
http://<host>:8123/api/sse
```
连接后，可以看到以下工具：
- `createNote(title, content)`
- `appendNote(title, content)`
- `readNote(title)`
- `listNotes()`
- `deleteNote(title)`

## 配置项
| 变量 | 默认值 | 说明 |
|---|---|---|
| `DASHSCOPE_API_KEY` | `your-api-key` | 阿里云百练 API Key，必填 |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | Chat 模型 |
| `SEARCH_API_KEY` | `your-search-api-key` | SearchAPI Key（`WebSearchTool` 使用） |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | 可选 Ollama 地址 |
| `MCP_SERVER_ENABLED` | `true` | 是否启用 MCP 服务端 |
| `NOTE_BASE_DIR` | `${user.dir}/tmp/notes` | `NoteSkill` 笔记存储根目录（容器中建议挂卷） |
| `SERVER_PORT` | `8123` | HTTP 端口 |
| `SPRING_PROFILES_ACTIVE` | `local` | 运行 profile |

## 启用 PgVector RAG（可选）
1. 在 `docker-compose.yml` 中取消 `pgvector` 服务注释。
2. 在 `application.yml` 打开 `spring.datasource.*` 与 `spring.ai.vectorstore.pgvector.*`。
3. 在 `SxwAiAgentApplication` 里移除 `DataSourceAutoConfiguration` 的 `exclude`。
4. 在 `PgVectorVectorStoreConfig` 类上加回 `@Configuration`。
