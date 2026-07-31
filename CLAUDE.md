# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
For project architecture, design principles, and development rules, see [AGENTS.md](AGENTS.md).

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

# Frontend (React + Vite, in frontend/ directory)
cd frontend && npm ci && npm run dev

# Docker
docker compose --env-file .env up -d --build
```

## Architecture

**Stack:** Spring Boot 3.4 + Spring AI Alibaba 1.0 + JDK 21 + Maven. LLM provider is DashScope (qwen-plus); Ollama available as optional fallback.

**Database:** PostgreSQL + PgVector, managed by Flyway (enabled by default, migrations in `src/main/resources/db/migration/`).

**Frontend:** Independent React 19 + Vite SPA in `frontend/` (not embedded in resources/static).

### Agent Runtime

Two runtime implementations (see AGENTS.md for full architecture):
- **ToolUseLoopRuntime** (primary): model returns `tool_use` → execute → loop; returns `end_turn` → finish.
- **LegacyReActRuntime** (compat): wraps SxwManus ReAct agent for backward compatibility.

Legacy agent class hierarchy (still present for LegacyReActRuntime):

```
BaseAgent          — state machine (IDLE→RUNNING→FINISHED/ERROR), message history, step loop
  └─ ReActAgent   — think()/act() pattern in each step
       └─ ToolCallAgent — calls LLM with available tools, executes via ToolCallingManager
            └─ SxwManus — system prompt + skill manifest, max 8 steps
```

### Agent Skills (progressive disclosure)

`SkillRegistry` scans `classpath*:skills/*/SKILL.md` at startup, parses YAML frontmatter. `SkillTool` exposes `listSkills()` and `loadSkill(name)` as `@Tool` methods — agents load full skill content on demand.

### MCP

Skills annotated with `@Tool` are dual-use: internal agents call them directly, and the MCP server (`spring-ai-starter-mcp-server-webmvc`) exposes them to external clients via SSE at `/api/sse`.

### RAG & Knowledge

Knowledge retrieval uses PgVector vector store (configured in `application.yml` under `spring.ai.vectorstore.pgvector`).
`LoveAppRagCustomAdvisorFactory` chains `DocumentRetrieverAdvisor` + `ContextualQueryAugmenter` with keyword enrichment.

### Eval harness

YAML golden dataset (`src/main/resources/eval/love-app.yaml`). `EvalRunner` applies evaluators:
- `KeywordContainsEvaluator` — checks for expected keywords
- `LlmJudgeEvaluator` — LLM-as-judge with constrained JSON output
Outputs markdown report to `target/eval-report.md`. `maven-surefire-plugin` excludes `eval` group by default.

### Proxy advisors (middleware chain)

- `MyLoggerAdvisor` — logs every LLM call + Micrometer metrics
- `LlmAnswerCacheAdvisor` — Caffeine-based semantic cache
- `DashScopeResilienceAdvisor` — resilience4j (retry 3x, rate limit 30/s, circuit breaker)
- `ReReadingAdvisor` — re-reading pattern (available but not enabled by default)

### Tool sandbox

- `TerminalOperationTool` — disabled by default (`TERMINAL_TOOL_ENABLED=false`), whitelist-only
- `NoteSkill` — file ops restricted to `NOTE_BASE_DIR`, max 1MB
- `ToolSandboxSupport` — path traversal prevention

### Configuration

- `.env.example` lists all env vars; `application.yml` references them with defaults
- Profile: `local` by default, `prod` in Docker
- Custom properties under `sxw.*` prefix
- Resilience4j config under `resilience4j.*`
- Management endpoints at `/api/actuator/*` (health, prometheus)
