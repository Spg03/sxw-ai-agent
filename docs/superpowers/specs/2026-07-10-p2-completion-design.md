# P2 Completion Design Spec

**Date:** 2026-07-10
**Scope:** Complete 3 partial P2 tasks from the production optimization plan
**Status:** Approved

---

## Overview

Three P2 tasks are partially complete and need finishing:

| Task | Current State | Goal |
|------|---------------|------|
| P2-2 | Dual eval systems, LLM-as-Judge not in DB path | Enhance EvalExecutor with LLM-as-Judge |
| P2-5 | React frontend built but not served by Spring Boot | Maven build integration |
| P2-6 | SHA-256 dedup exists, no force reindex or change detection | Smart dedup + force reindex + change detection |

---

## P2-2: Enhance EvalExecutor with LLM-as-Judge

### Context

Two eval systems coexist independently:

1. **EvalRunner** (`infrastructure.eval`) -- YAML-driven, uses `LlmJudgeEvaluator` + `KeywordContainsEvaluator`, targets LoveApp only. Invoked via `mvn test -Dgroups=eval`.
2. **EvalService + EvalExecutor** (`evaluation`) -- DB-backed, multi-profile, uses `AgentRuntime`, but `validateOutput()` only does substring matching (line 155 has a TODO for more complex validation).

### Decision

Enhance `EvalExecutor` by injecting `LlmJudgeEvaluator` as an additional validation strategy. Keep `EvalRunner` as an offline YAML testing tool (no changes).

### Changes

#### 1. Database Schema (new migration `V3__eval_llm_judge.sql`)

```sql
-- Add judge_criteria column to eval cases
ALTER TABLE ai_eval_case ADD COLUMN IF NOT EXISTS judge_criteria TEXT;

-- Add individual eval result table for detailed per-case results
CREATE TABLE IF NOT EXISTS ai_eval_result (
    id          BIGSERIAL PRIMARY KEY,
    run_id      VARCHAR(64) NOT NULL,
    case_id     VARCHAR(64) NOT NULL,
    case_name   VARCHAR(255),
    passed      BOOLEAN NOT NULL DEFAULT FALSE,
    score       DOUBLE PRECISION,
    judge_reason TEXT,
    actual_output TEXT,
    expected_output TEXT,
    validation_details TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eval_result_run ON ai_eval_result(run_id);
CREATE INDEX IF NOT EXISTS idx_eval_result_case ON ai_eval_result(case_id);
```

#### 2. EvalCase Record

Add `judgeCriteria` field (nullable String):

```java
public record EvalCase(
    Long id, String caseId, String caseName, EvalCaseType caseType,
    EvalCaseStatus status, String profileCode, String inputPrompt,
    String expectedOutput, String validationRules,
    String judgeCriteria,       // <-- NEW: LLM judge prompt
    List<String> tags, Integer priority,
    String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt
) { ... }
```

#### 3. EvalResult Record

Add judge-related fields:

```java
public record EvalResult(
    String caseId, String caseName, boolean passed,
    String actualOutput, String expectedOutput,
    String validationDetails, long durationMs, String errorMessage,
    Double judgeScore,          // <-- NEW: 0.0~1.0 from LLM judge
    String judgeReason          // <-- NEW: reason from LLM judge
) { ... }
```

#### 4. EvalExecutor Enhancement

- Constructor inject `ChatModel` (with `@Autowired(required = false)` to avoid startup failure)
- Create `LlmJudgeEvaluator` instance if `ChatModel` is available
- `validateOutput()` becomes two-phase:
  - Phase 1: keyword/substring match (existing logic)
  - Phase 2: if `judgeCriteria` is non-null, run LLM-as-Judge
  - Final pass = keyword_pass AND (no_criteria OR judge_pass)
- Save each case result to `ai_eval_result` table via new `EvalResultRepository`

#### 5. EvalCaseRepository

- Update all SELECT queries to include `judge_criteria` column
- Add `updateJudgeCriteria(caseId, criteria)` method

#### 6. EvalService

- Update `createCase()` to accept optional `judgeCriteria` parameter
- Update `executeRun()` to persist individual results to `ai_eval_result`
- Add `getRunResults(runId)` method to query detailed results

#### 7. EvalController

- Update create case DTO to include optional `judgeCriteria`
- Add `GET /eval/runs/{runId}/results` endpoint for detailed results

### Files Affected

- `evaluation/EvalExecutor.java` -- add LLM judge integration
- `evaluation/EvalCase.java` -- add `judgeCriteria` field
- `evaluation/EvalResult.java` -- add `judgeScore`, `judgeReason`
- `evaluation/EvalCaseRepository.java` -- update queries
- `evaluation/EvalResultRepository.java` -- NEW: persist individual results
- `evaluation/EvalService.java` -- wire result persistence
- `web/controller/EvalController.java` -- update API
- `db/migration/V3__eval_llm_judge.sql` -- NEW migration

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

Use `frontend-maven-plugin` to build the React app during `mvn package` and output to `src/main/resources/static/`. Delete the old `index.html`.

### Changes

#### 1. `frontend/vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../src/main/resources/static',
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

#### 2. `pom.xml` -- Add `frontend-maven-plugin`

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.1</version>
    <configuration>
        <workingDirectory>frontend</workingDirectory>
        <nodeVersion>v20.18.0</nodeVersion>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>install</arguments>
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
```

#### 3. SPA Fallback -- `WebConfig.java` (new or modify existing)

Spring Boot needs to forward non-API, non-static-resource requests to `index.html` so that React Router works on direct URL access and page refresh.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward SPA routes to index.html (exclude API paths)
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}/{subpath:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
```

#### 4. Delete Old Frontend

- Delete `src/main/resources/static/index.html` (the 1263-line Vue monolith)
- After Vite build, this path will be populated by the React build output

#### 5. `.gitignore` Update

Add `src/main/resources/static/` to `.gitignore` (the build output should not be committed, it is generated by Maven).

### Files Affected

- `frontend/vite.config.ts` -- update build output dir and proxy
- `pom.xml` -- add frontend-maven-plugin
- `WebConfig.java` -- NEW or modify: SPA fallback routing
- `src/main/resources/static/index.html` -- DELETE (replaced by Vite build)
- `.gitignore` -- add static/ build output

---

## P2-6: Smart Dedup + Force Reindex + Change Detection

### Context

`DocumentIngestService.ingest()` currently:
- Computes SHA-256 of content
- If hash matches an existing document: skips (returns "Already indexed")
- If hash is new: splits, embeds, saves

Gaps:
- Content changes require manual deletion then re-upload
- No force reindex capability
- `deleteDocument()` works (cascades chunks) but no explicit reindex endpoint

### Decision

Add force parameter for reindexing, automatic change detection (delete-then-insert on hash mismatch), and a dedicated reindex endpoint.

### Changes

#### 1. `DocumentIngestService.ingest()` -- Enhanced Logic

```
ingest(title, sourcePath, content):
  contentHash = sha256(content)
  existing = findByContentHash(contentHash)

  if existing AND NOT force:
    updateDocument(existing.docId)  // refresh timestamp
    return "Already indexed"

  if existing AND force:
    deleteDocument(existing.docId)  // remove old chunks + doc
    // fall through to re-ingest

  // Also check by title for content-change detection
  existingByTitle = findByTitle(title)
  if existingByTitle AND existingByTitle.contentHash != contentHash:
    deleteDocument(existingByTitle.docId)  // content changed, reindex
    // fall through to re-ingest

  // Standard ingest: split, embed, save
  ...
```

New overload: `ingest(title, sourcePath, content, force)`

#### 2. `KnowledgeController` -- Updated Endpoints

```java
@PostMapping("/documents")
Result<IngestResult> uploadDocument(
    @RequestParam("file") MultipartFile file,
    @RequestParam(defaultValue = "false") boolean force  // NEW
)

@PostMapping("/documents/text")
Result<IngestResult> ingestText(
    @RequestParam @NotBlank String title,
    @RequestParam(required = false) String sourcePath,
    @RequestParam(defaultValue = "false") boolean force,  // NEW
    @RequestBody @NotBlank String content
)

@PutMapping("/documents/{docId}/reindex")    // NEW endpoint
Result<IngestResult> reindexDocument(@PathVariable String docId)
```

#### 3. `KnowledgeRepository` -- New Methods

```java
Optional<KnowledgeDocumentRecord> findByDocId(String docId);
Optional<KnowledgeDocumentRecord> findByTitle(String title);
```

#### 4. `DocumentIngestService.reindex()` -- New Method

```java
public IngestResult reindex(String docId) {
    Optional<KnowledgeDocumentRecord> doc = knowledgeRepository.findByDocId(docId);
    if (doc.isEmpty()) throw new IllegalArgumentException("Document not found: " + docId);

    KnowledgeDocumentRecord record = doc.get();
    // Read content from sourcePath if possible, otherwise return error
    String content = readSourceContent(record.sourcePath());
    return ingest(record.title(), record.sourcePath(), content, true);
}
```

### Files Affected

- `knowledge/DocumentIngestService.java` -- add force param, change detection, reindex method
- `knowledge/KnowledgeRepository.java` -- add `findByDocId()`, `findByTitle()`
- `web/controller/KnowledgeController.java` -- add force param, reindex endpoint

---

## Implementation Order

1. **P2-6** (Knowledge incremental) -- smallest scope, no cross-module deps
2. **P2-2** (Eval merge) -- medium scope, self-contained in eval package
3. **P2-5** (Frontend integration) -- requires build config changes, do last to avoid disrupting dev workflow

## Testing Strategy

- **P2-6:** Unit test for `DocumentIngestService` force reindex, integration test for reindex endpoint
- **P2-2:** Unit test for `EvalExecutor` two-phase validation, integration test for eval run with LLM judge
- **P2-5:** Manual verification: `mvn package` produces static files, Spring Boot serves React app, SPA routing works
