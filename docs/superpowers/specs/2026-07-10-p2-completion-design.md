# P2 Completion Design Spec

**Date:** 2026-07-10
**Scope:** Complete 3 partial P2 tasks from the production optimization plan
**Status:** Approved (v2 — incorporates code review feedback)

---

## Overview

Three P2 tasks are partially complete and need finishing:

| Task | Current State | Goal |
|------|---------------|------|
| P2-2 | Dual eval systems, LLM-as-Judge not in DB path | Enhance EvalExecutor with LLM-as-Judge |
| P2-5 | React frontend built but not served by Spring Boot | Maven build integration |
| P2-6 | SHA-256 dedup exists, no force reindex or change detection | Atomic reindex with fingerprint-based change detection |

---

## P2-6: Atomic Reindex with Fingerprint-Based Change Detection

### Context

`DocumentIngestService.ingest()` currently:
- Computes SHA-256 of content
- If hash matches an existing document: skips (returns "Already indexed")
- If hash is new: splits, embeds, saves

Gaps:
- Content changes require manual deletion then re-upload
- No force reindex capability
- `contentHash` alone cannot detect embedding model or chunking strategy changes
- `deleteDocument()` + re-ingest is non-atomic: embedding failure after delete loses the document
- `findByTitle()` is unsafe as document identity (titles are not unique)
- `reindex()` relies on `sourcePath` which may not persist across restarts

### Decision

Introduce stable document identity (`docId`-based), index fingerprint for comprehensive change detection, and atomic chunk replacement within a DB transaction. The reindex endpoint requires content re-upload rather than relying on sourcePath.

### Changes

#### 1. Index Fingerprint

Replace simple `contentHash` comparison with a composite fingerprint that captures the full indexing pipeline state:

```java
public static String computeIndexFingerprint(String contentHash, IndexConfig config) {
    String raw = contentHash
        + "|" + config.embeddingModel()
        + "|" + config.embeddingModelVersion()
        + "|" + config.chunkSize()
        + "|" + config.chunkOverlap()
        + "|" + config.parserVersion();
    return sha256(raw);
}
```

`IndexConfig` is derived from `application.yml` properties (`sxw.knowledge.*`). The fingerprint is stored in `ai_knowledge_document.index_fingerprint`.

When deciding whether to skip: compare `indexFingerprint` (not just `contentHash`). This ensures that changing the embedding model or chunk strategy triggers re-indexing even if content is unchanged.

#### 2. IngestStatus Enum

```java
public enum IngestStatus {
    CREATED,     // new document ingested
    UPDATED,     // content changed, chunks replaced atomically
    REINDEXED,   // force=true, chunks replaced regardless
    SKIPPED      // fingerprint matches, no changes needed
}
```

`IngestResult` updated:

```java
public record IngestResult(
    String docId,
    int chunkCount,
    String message,
    IngestStatus status    // NEW
) {
    public boolean isSuccess() {
        return docId != null;
    }
}
```

#### 3. Document Identity: docId-Based, Not Title-Based

**Do NOT use `findByTitle()` for identity resolution.** Titles are display-only and not unique.

All reindex and update operations use `docId` as the stable identifier:
- `ingest()` creates a new `docId` for new documents
- `reindex(docId, content)` updates the existing `docId` in-place
- `deleteDocument(docId)` removes by `docId`

#### 4. Atomic Chunk Replacement

**Never delete old data before new data is ready.** The ingest flow is:

```
ingest(documentIdentity, content, force):

  contentHash = sha256(content)
  indexFingerprint = computeFingerprint(contentHash, indexConfig)

  existing = findByIdentity(documentIdentity)  // by docId or documentKey

  if existing exists
      AND existing.indexFingerprint == indexFingerprint
      AND force == false:
          refreshTimestamp(existing.docId)
          return IngestResult(existing.docId, existing.chunkCount, "No changes", SKIPPED)

  // Phase 1: Prepare new data (no DB writes yet)
  newChunks = textSplitter.split(content)
  newVectors = embeddingService.embedBatch(newChunks)

  if newChunks.isEmpty() or newVectors.isEmpty():
      return IngestResult(null, 0, "Failed to generate chunks/vectors", null)

  // Phase 2: Atomic DB transaction
  @Transactional:
      if existing exists:
          // Keep the same docId — update metadata, replace chunks
          deleteChunksByDocId(existing.docId)     // old chunks gone
          updateDocumentMetadata(existing.docId,  // update title, hash, fingerprint, chunkCount
              title, sourcePath, newChunks.size(),
              contentHash, indexFingerprint)
          saveChunks(existing.docId, newChunks, newVectors)  // new chunks in
          return IngestResult(existing.docId, newChunks.size(), "Updated", UPDATED)
      else:
          newDocId = saveDocument(title, sourcePath, newChunks.size(),
              contentHash, indexFingerprint)
          saveChunks(newDocId, newChunks, newVectors)
          return IngestResult(newDocId, newChunks.size(), "Created", CREATED)
```

This preserves `docId` across re-indexes, maintaining references from permissions, bookmarks, eval data, operation logs, and external systems.

#### 5. Reindex Endpoint — Content Re-Upload

The `sourcePath` from file upload is a temp path that does not persist. Text input may have no `sourcePath` at all. Therefore, reindex requires the caller to provide content:

```java
@PutMapping("/documents/{docId}/content")
Result<IngestResult> reindexDocument(
    @PathVariable String docId,
    @RequestBody @NotBlank String content
)
```

If the document does not exist:

```
404 Not Found: Document not found: {docId}
```

If content is empty:

```
400 Bad Request: Content must not be empty
```

#### 6. KnowledgeRepository — Updated Methods

```java
// Find document by stable docId
Optional<KnowledgeDocumentRecord> findByDocId(String docId);

// Delete only chunks (not the document record) for atomic replacement
void deleteChunksByDocId(String docId);

// Update document metadata in-place (preserves docId)
void updateDocumentMetadata(String docId, String title, String sourcePath,
    int chunkCount, String contentHash, String indexFingerprint);
```

Remove `findByTitle()` — titles are not identity.

The `KnowledgeDocumentRecord` adds `contentHash` and `indexFingerprint` fields:

```java
public record KnowledgeDocumentRecord(
    String docId, String title, String sourcePath,
    int chunkCount, String status,
    String contentHash,           // NEW
    String indexFingerprint,      // NEW
    Instant createdAt
) {}
```

#### 7. Database Schema (new migration `V3__knowledge_fingerprint.sql`)

```sql
-- Add fingerprint columns to knowledge document
ALTER TABLE ai_knowledge_document ADD COLUMN IF NOT EXISTS content_hash TEXT;
ALTER TABLE ai_knowledge_document ADD COLUMN IF NOT EXISTS index_fingerprint TEXT;

-- Index for fingerprint-based lookup
CREATE INDEX IF NOT EXISTS idx_knowledge_fingerprint
    ON ai_knowledge_document(index_fingerprint);
```

Note: `content_hash` column may already exist from V2 migration. The `IF NOT EXISTS` handles this safely.

### Files Affected

- `knowledge/DocumentIngestService.java` — rewrite `ingest()` with atomic flow, add `IndexConfig`, `IngestStatus`
- `knowledge/KnowledgeRepository.java` — add `findByDocId()`, `deleteChunksByDocId()`, `updateDocumentMetadata()`; remove `findByTitle()`
- `web/controller/KnowledgeController.java` — change reindex endpoint to content re-upload
- `knowledge/IndexConfig.java` — NEW: captures embedding model, chunk strategy, parser version
- `db/migration/V3__knowledge_fingerprint.sql` — NEW migration

---

## P2-2: Enhance EvalExecutor with LLM-as-Judge

### Context

Two eval systems coexist independently:

1. **EvalRunner** (`infrastructure.eval`) — YAML-driven, uses `LlmJudgeEvaluator` + `KeywordContainsEvaluator`, targets LoveApp only. Invoked via `mvn test -Dgroups=eval`.
2. **EvalService + EvalExecutor** (`evaluation`) — DB-backed, multi-profile, uses `AgentRuntime`, but `validateOutput()` only does substring matching (line 155 has a TODO for more complex validation).

### Decision

Enhance `EvalExecutor` to support LLM-as-Judge as an additional validation strategy, with configurable validation modes. Keep `EvalRunner` as an offline YAML testing tool (no changes). Clear separation: `EvalExecutor` executes and returns results, `EvalService` persists results.

### Changes

#### 1. ValidationMode Enum

```java
public enum ValidationMode {
    KEYWORD_ONLY,   // only keyword/substring matching (backward-compatible default)
    LLM_ONLY,       // only LLM-as-Judge
    ALL,            // all configured strategies must pass
    ANY             // any one strategy passing is sufficient
}
```

Old cases with no `validationMode` default to `KEYWORD_ONLY`, preserving backward compatibility.

#### 2. Database Schema (new migration `V3__eval_llm_judge.sql`)

Note: if P2-6 is implemented first, this becomes `V4__eval_llm_judge.sql`.

```sql
-- Add judge and validation mode columns to eval cases
ALTER TABLE ai_eval_case ADD COLUMN IF NOT EXISTS judge_criteria TEXT;
ALTER TABLE ai_eval_case ADD COLUMN IF NOT EXISTS validation_mode VARCHAR(20) DEFAULT 'KEYWORD_ONLY';

-- Individual eval result table
CREATE TABLE IF NOT EXISTS ai_eval_result (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              VARCHAR(64) NOT NULL,
    case_id             VARCHAR(64) NOT NULL,
    case_name           VARCHAR(255),
    passed              BOOLEAN NOT NULL DEFAULT FALSE,
    keyword_passed      BOOLEAN,
    judge_status        VARCHAR(20),        -- PASSED, FAILED, UNAVAILABLE, TIMEOUT, PARSE_ERROR
    judge_model         VARCHAR(128),
    judge_prompt_version VARCHAR(20),
    judge_score         DOUBLE PRECISION,
    judge_reason        TEXT,
    score               DOUBLE PRECISION,
    actual_output       TEXT,
    expected_output     TEXT,
    validation_details  TEXT,
    validation_mode     VARCHAR(20),
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    error_message       TEXT,
    attempt_no          INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, case_id, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_eval_result_run ON ai_eval_result(run_id);
CREATE INDEX IF NOT EXISTS idx_eval_result_case ON ai_eval_result(case_id);
```

#### 3. EvalCase Record

Add `judgeCriteria` and `validationMode` fields:

```java
public record EvalCase(
    Long id, String caseId, String caseName, EvalCaseType caseType,
    EvalCaseStatus status, String profileCode, String inputPrompt,
    String expectedOutput, String validationRules,
    String judgeCriteria,           // NEW: LLM judge prompt (nullable)
    ValidationMode validationMode,  // NEW: defaults to KEYWORD_ONLY
    List<String> tags, Integer priority,
    String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt
) { ... }
```

#### 4. EvalResult Record

Expanded judge details:

```java
public record EvalResult(
    String caseId, String caseName, boolean passed,
    boolean keywordPassed,              // NEW: keyword check result
    String actualOutput, String expectedOutput,
    String validationDetails, long durationMs, String errorMessage,
    JudgeStatus judgeStatus,            // NEW: PASSED, FAILED, UNAVAILABLE, TIMEOUT, PARSE_ERROR
    String judgeModel,                  // NEW: model name used for judging
    Double judgeScore,                  // NEW: 0.0~1.0
    String judgeReason,                 // NEW: reason from judge
    ValidationMode validationMode       // NEW: which mode was used
) { ... }

public enum JudgeStatus {
    PASSED, FAILED, UNAVAILABLE, TIMEOUT, PARSE_ERROR
}
```

#### 5. EvalExecutor — Execution Only, No Persistence

**Clear separation of concerns:**

- `EvalExecutor`: executes cases against the agent runtime, runs validation, returns `EvalResult` objects. **Does NOT write to database.**
- `EvalService`: orchestrates runs, calls `EvalExecutor`, persists results to `ai_eval_result`.

```java
@Service
public class EvalExecutor {

    private final List<AgentProfile> profileList;
    private final List<AgentRuntime> runtimeList;
    private final ObjectProvider<LlmJudgeEvaluator> judgeProvider;  // optional

    public EvalExecutor(
        List<AgentProfile> profileList,
        List<AgentRuntime> runtimeList,
        ObjectProvider<LlmJudgeEvaluator> judgeProvider
    ) { ... }

    public EvalResult execute(EvalCase evalCase) {
        // 1. Run agent via runtime
        // 2. Run keyword validation
        // 3. Run LLM judge if criteria present
        // 4. Combine results per validationMode
        // 5. Return EvalResult (no DB writes)
    }
}
```

#### 6. Judge Protocol

The LLM judge follows a strict protocol:

| Parameter | Value |
|-----------|-------|
| Score range | 0.0 ~ 1.0 |
| Pass threshold | >= 0.7 (configurable via `sxw.eval.judge.pass-threshold`) |
| Temperature | 0 (deterministic) |
| Output format | Strict JSON: `{"pass": bool, "score": float, "reason": string}` |
| Max input length | 4000 chars (truncate actual output if exceeded) |
| Timeout | 30 seconds |
| Retry | 0 (no retry — record TIMEOUT status) |
| Model | Use project's configured ChatModel |

**ChatModel unavailable behavior:** If a case has `judgeCriteria` but no `ChatModel` is configured:
- `judgeStatus = UNAVAILABLE`
- `passed = false` (do NOT silently skip and pass)
- `errorMessage = "LLM judge is not configured but judgeCriteria is set"`

**JSON parse failure:** If the judge returns non-parseable output:
- `judgeStatus = PARSE_ERROR`
- `passed = false`
- `judgeReason = "judge response not parseable: <truncated raw>"`

**Timeout:** If the judge call exceeds 30s:
- `judgeStatus = TIMEOUT`
- `passed = false`
- `judgeReason = "judge call timed out after 30s"`

#### 7. LlmJudgeEvaluator Bean Configuration

Create a `@Configuration` class to register `LlmJudgeEvaluator` as an optional bean:

```java
@Configuration
public class EvalJudgeConfig {

    @Bean
    @ConditionalOnBean(ChatModel.class)
    public LlmJudgeEvaluator llmJudgeEvaluator(ChatModel chatModel) {
        return new LlmJudgeEvaluator(chatModel);
    }
}
```

`EvalExecutor` injects via `ObjectProvider<LlmJudgeEvaluator>` — if the bean is absent, judge is unavailable.

#### 8. EvalService — Orchestration and Persistence

`EvalService` is the only class that writes to `EvalResultRepository`:

```java
public void executeRun(String runId) {
    // ... existing run setup ...
    List<EvalResult> results = evalExecutor.executeBatch(cases);

    // Persist individual results
    evalResultRepository.saveBatch(runId, results);

    // Update run summary
    evalRunRepository.updateResults(runId, ...);
}

public List<EvalResult> getRunResults(String runId) {
    return evalResultRepository.findByRunId(runId);
}
```

#### 9. EvalController Updates

- Create case DTO adds optional `judgeCriteria` and `validationMode`
- `GET /api/eval/runs/{runId}/results` — returns detailed per-case results

### Files Affected

- `evaluation/ValidationMode.java` — NEW enum
- `evaluation/JudgeStatus.java` — NEW enum
- `evaluation/EvalCase.java` — add `judgeCriteria`, `validationMode`
- `evaluation/EvalResult.java` — add judge fields
- `evaluation/EvalExecutor.java` — add judge integration, execution-only (no DB writes)
- `evaluation/EvalJudgeConfig.java` — NEW: optional LlmJudgeEvaluator bean
- `evaluation/EvalResultRepository.java` — NEW: persist individual results
- `evaluation/EvalCaseRepository.java` — update queries for new columns
- `evaluation/EvalService.java` — wire result persistence, add `getRunResults()`
- `web/controller/EvalController.java` — update API, add results endpoint
- `db/migration/V3__eval_llm_judge.sql` (or V4) — NEW migration

---

## P2-5: Frontend Maven Build Integration

### Context

A complete React frontend exists in `frontend/` with:
- React 19 + Vite 8 + Tailwind 4 + react-router-dom 7
- Pages: Dashboard, Chat, Treehole, Notes, Eval, Skills, Traces, Login
- Components: ErrorBoundary, Pagination, Layout
- API client with AbortController support
- AuthContext with JWT refresh token

But Spring Boot still serves the old `static/index.html` (1263-line Vue 3 monolith).

### Decision

Use `frontend-maven-plugin` to build the React app during `mvn package`. Vite outputs to `frontend/dist/`, then Maven copies it to `target/classes/static/`. Source directory `src/main/resources/static/` is never written to by the build.

### Changes

#### 1. `frontend/vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: 'dist',            // standard Vite output dir, NOT src/
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8123',   // Fixed: was 8080
        changeOrigin: true,
      }
    }
  }
})
```

#### 2. `pom.xml` — Add `frontend-maven-plugin` + Resource Copy

Two steps: (1) build the frontend, (2) copy dist to `target/classes/static/`.

```xml
<!-- Step 1: Build frontend -->
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.1</version>
    <configuration>
        <workingDirectory>frontend</workingDirectory>
        <nodeVersion>v20.18.0</nodeVersion>
        <skip>${skip.frontend}</skip>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-ci</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>ci</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- Step 2: Copy frontend dist to target/classes/static -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-frontend</id>
            <phase>generate-resources</phase>
            <goals><goal>copy-resources</goal></goals>
            <configuration>
                <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
                <resources>
                    <resource>
                        <directory>frontend/dist</directory>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Properties:

```xml
<properties>
    <java.version>21</java.version>
    <skip.frontend>false</skip.frontend>
</properties>
```

Usage:

```bash
# Full build (includes frontend)
mvn package

# Java-only build (skip frontend for faster iteration)
mvn package -Dskip.frontend=true

# Tests (skip frontend by default to avoid Node install)
mvn test -Dskip.frontend=true
```

#### 3. SPA Fallback — Arbitrary Depth, Exclude API Paths

Use a controller-based approach (not `ViewControllerRegistry`) to handle arbitrary-depth React Router paths while excluding backend paths:

```java
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final List<String> EXCLUDED_PREFIXES = List.of(
        "/api/",
        "/actuator/",
        "/v3/api-docs",
        "/swagger-ui",
        "/sse"
    );

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Catch-all for SPA routes: any path without a file extension
        // that doesn't start with excluded prefixes
        registry.addViewController("/**/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");
    }
}
```

Additionally, a `SpaForwardController` handles the exclusion logic:

```java
@Controller
public class SpaForwardController {

    @RequestMapping(value = {
        "/",
        "/chat", "/chat/**",
        "/treehole", "/treehole/**",
        "/notes", "/notes/**",
        "/eval", "/eval/**",
        "/skills", "/skills/**",
        "/traces", "/traces/**",
        "/dashboard", "/dashboard/**",
        "/login"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
```

This explicitly whitelists known SPA routes and avoids accidentally catching `/api/**` or `/actuator/**`.

#### 4. Security — Allow Static Resources

Ensure `SecurityConfig` permits access to SPA resources:

```java
.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
```

#### 5. Delete Old Frontend

- Delete `src/main/resources/static/index.html` (the 1263-line Vue monolith)
- Keep `src/main/resources/static/` directory with only a `.gitkeep` or `README.md` explaining that build output goes to `target/classes/static/`

#### 6. `.gitignore` Update

```
# Frontend build output (generated by Maven, not committed)
frontend/dist/
frontend/node_modules/
frontend/node/
```

Do NOT add `src/main/resources/static/` to `.gitignore` — it may contain hand-maintained resources.

#### 7. Test Cases

Verify:

```
GET /chat                     → index.html (SPA)
GET /eval/runs/123/detail     → index.html (deep SPA route)
GET /api/eval/runs/123        → EvalController (backend)
GET /assets/index-xxx.js      → static resource
GET /v3/api-docs              → API docs (not forwarded)
GET /actuator/health          → health endpoint (not forwarded)
mvn clean package             → builds successfully, JAR contains static files
mvn test -Dskip.frontend=true → skips Node install and frontend build
```

### Files Affected

- `frontend/vite.config.ts` — output to `dist/`, fix proxy target
- `pom.xml` — add frontend-maven-plugin + maven-resources-plugin copy, add `skip.frontend` property
- `SpaWebConfig.java` — NEW: SPA fallback with exclusion list
- `SpaForwardController.java` — NEW: explicit SPA route whitelist
- `common/config/SecurityConfig.java` — permit static resources
- `src/main/resources/static/index.html` — DELETE (replaced by Vite build)
- `.gitignore` — add `frontend/dist/`, `frontend/node_modules/`, `frontend/node/`

---

## Implementation Order

1. **P2-6** (Knowledge incremental) — smallest scope, no cross-module deps
2. **P2-2** (Eval merge) — medium scope, self-contained in eval package
3. **P2-5** (Frontend integration) — requires build config changes, do last to avoid disrupting dev workflow

---

## Testing Strategy

### P2-6 Test Cases

| Scenario | Expected |
|----------|----------|
| Same doc, same content, force=false | SKIPPED |
| Same doc, same content, force=true | REINDEXED |
| Same doc, content changed | UPDATED |
| Different doc, same title | No interference |
| Different doc, same content | No accidental deletion |
| Embedding service failure | Old index remains available, return error |
| Concurrent upload of same doc | No duplicate records (DB unique constraint on docId) |
| Reindex with content re-upload | Chunks replaced atomically, same docId |
| Reindex nonexistent docId | 404 Not Found |
| Empty content on reindex | 400 Bad Request |
| Change embedding model in config | Fingerprint changes → UPDATED on next ingest |

### P2-2 Test Cases

| Scenario | Expected |
|----------|----------|
| No judgeCriteria, KEYWORD_ONLY | Keyword-only behavior preserved |
| LLM_ONLY mode | No keyword check, judge-only result |
| ALL mode, both pass | passed = true |
| ALL mode, keyword pass + judge fail | passed = false |
| ANY mode, keyword fail + judge pass | passed = true |
| ChatModel not configured | judgeStatus = UNAVAILABLE, passed = false |
| Judge timeout (>30s) | judgeStatus = TIMEOUT, passed = false |
| Judge returns invalid JSON | judgeStatus = PARSE_ERROR, passed = false |
| Score exactly at threshold (0.7) | passed = true (>= threshold) |
| Score just below threshold (0.69) | passed = false |
| EvalResult persisted with attempt_no | Re-runs distinguishable via UNIQUE(run_id, case_id, attempt_no) |
| EvalExecutor unit test | No DB dependency, returns EvalResult only |

### P2-5 Test Cases

| Scenario | Expected |
|----------|----------|
| `mvn clean package` | Builds successfully, JAR contains static files |
| `mvn test -Dskip.frontend=true` | Skips Node install and frontend build |
| `npm ci` (not `npm install`) | Deterministic dependency install |
| JAR contains `index.html` | Verified in `target/classes/static/` |
| JAR contains `assets/` | Verified in `target/classes/static/assets/` |
| `src/main/resources/static/` unchanged by build | No build artifacts in source directory |
| Deep SPA route refresh (e.g., `/eval/runs/123/detail`) | Returns `index.html` |
| API path not caught by SPA fallback | `/api/eval/runs` → EvalController |
| `/actuator/health` not forwarded | Returns health JSON |
| `/v3/api-docs` not forwarded | Returns Swagger spec |
| `/assets/index-xxx.js` served | Static resource served correctly |
