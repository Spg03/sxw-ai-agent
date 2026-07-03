# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Run (JDK 21 required, DASHSCOPE_API_KEY must be set)
DASHSCOPE_API_KEY=sk-xxx mvn spring-boot:run
# App starts on port 8123, context-path /api
# Health: http://localhost:8123/api/health
# API docs: http://localhost:8123/api/doc.html

# Tests (eval suite excluded by default to avoid burning LLM tokens)
mvn test

# Run a single test class
mvn test -Dtest=LoveAppTest

# Run eval suite manually (requires DASHSCOPE_API_KEY)
mvn test -Dgroups=eval -Dtest=LoveAppEvalSuiteTest

# CI build (includes verify phase)
mvn verify

# Docker
docker compose --env-file .env up -d --build
```

## Architecture

**Stack:** Spring Boot 3.4 + Spring AI Alibaba 1.0 + JDK 21 + Maven. LLM provider is DashScope (qwen-plus); Ollama available as optional fallback.

**Database is disabled by default.** `SxwAiAgentApplication` excludes `DataSourceAutoConfiguration`. To enable PgVector: uncomment datasource/vectorstore config in `application.yml`, remove the exclude, add `@Configuration` back on `PgVectorVectorStoreConfig`.

### Two agent systems

**LoveApp** (`love/LoveApp.java`) — Stateless Spring `@Component`. Relationship advisor using a fixed `ChatClient` with an advisor chain: `MessageChatMemoryAdvisor` → `MyLoggerAdvisor` → optional `LlmAnswerCacheAdvisor` → optional `DashScopeResilienceAdvisor`. Supports plain chat, structured output (`entity(LoveReport.class)`), RAG, tool calling, and MCP tool calling. Uses `MessageWindowChatMemory` (in-memory, max 20 messages per conversation).

**SxwManus** (`manus/SxwManus.java`) — General-purpose ReAct agent. **NOT a Spring bean** — `AiController` creates a new instance per request to avoid concurrent state corruption. Agent class hierarchy:

```
BaseAgent          — state machine (IDLE→RUNNING→FINISHED/ERROR), message history, step loop, maxSteps, history trim
  └─ ReActAgent   — think()/act() pattern in each step
       └─ ToolCallAgent — concrete think/act: calls LLM with available tools, executes tool calls via ToolCallingManager
            └─ SxwManus — system prompt + skill manifest, max 8 steps
```

Key behaviors in `BaseAgent`:
- `run()` / `runStream()`: synchronous/SSE streaming execution loops
- `trimHistoryIfNeeded()`: caps `messageList` at `maxHistoryMessages` (default 60), preserves SystemMessage + aligns tool call/response pairs
- `onFinished` hook: fires exactly once via `AtomicBoolean`, used to persist `messageList` to `ManusMemoryStore`
- Async execution uses `agentTaskExecutor` (ThreadPoolExecutor: core=4, max=16, bounded queue=200, CallerRunsPolicy) — defined in `common/config/AgentExecutorConfig.java`

### Agent Skills (Anthropic-style progressive disclosure)

`SkillRegistry` scans `classpath*:skills/*/SKILL.md` at startup, parses YAML frontmatter (`name`, `description`, body). `manifest()` returns a summary injected into SxwManus's system prompt. `SkillTool` exposes `listSkills()` and `loadSkill(name)` as `@Tool` methods — agents only load full skill content on demand, saving ~85% prompt tokens.

### MCP

Skills annotated with `@Tool` (`NoteSkill`, `SkillTool`) are dual-use: internal agents call them directly, and the MCP server (`spring-ai-starter-mcp-server-webmvc`) exposes them to external clients (Claude Desktop, Cursor) via SSE at `/api/sse`.

### RAG

`LoveAppRagCustomAdvisorFactory` chains a `DocumentRetrieverAdvisor` + `ContextualQueryAugmenter` with keyword enrichment (`MyKeywordEnricher`). `QueryRewriter` rewrites user queries before retrieval. Currently uses in-memory `SimpleVectorStore` (configured in `LoveAppVectorStoreConfig`).

### Eval harness

YAML golden dataset (`src/main/resources/eval/love-app.yaml`) with 7 cases across 5 categories. `EvalRunner` runs each case through the target system, then applies evaluators:
- `KeywordContainsEvaluator` — checks for expected keywords
- `LlmJudgeEvaluator` — LLM-as-judge with constrained JSON output
Outputs markdown report to `target/eval-report.md`. `maven-surefire-plugin` excludes `eval` group by default.

### Proxy advisors (middleware chain)

- `MyLoggerAdvisor` — logs every LLM call + registers Micrometer metrics (latency, tokens, cache hit/miss)
- `LlmAnswerCacheAdvisor` — Caffeine-based semantic cache for identical questions
- `DashScopeResilienceAdvisor` — wraps DashScope calls with resilience4j (retry 3x, rate limit 30/s, circuit breaker)
- `ReReadingAdvisor` — re-reading pattern (available but not enabled by default)

### Tool sandbox

- `TerminalOperationTool` — disabled by default (`TERMINAL_TOOL_ENABLED=false`), whitelist-only commands
- `NoteSkill` — file ops restricted to `NOTE_BASE_DIR`, max file size 1MB
- `ToolSandboxSupport` — path traversal prevention for file operations

### Configuration

- `.env.example` lists all env vars; `application.yml` references them with defaults
- Profile: `local` by default, `prod` in Docker
- All custom properties under `sxw.*` prefix (skill, cache, tool)
- Resilience4j config under `resilience4j.*` for DashScope
- Management endpoints at `/api/actuator/*` (health, prometheus)

### Frontend

Single SPA at `src/main/resources/static/index.html`. Tabs: LoveApp chat, Manus agent, Skills browser, Notes manager, Monitoring dashboard (LLM cache hit rate, token usage, latency, agent steps).
