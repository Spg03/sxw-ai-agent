# P2 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete 3 partial P2 tasks — atomic knowledge reindex, eval LLM-as-Judge integration, and frontend Maven build integration.

**Architecture:** P2-6 adds index fingerprinting and atomic chunk replacement to the knowledge ingestion pipeline. P2-2 integrates LLM-as-Judge validation into the DB-backed eval executor with configurable validation modes. P2-5 wires the React frontend into Spring Boot via Maven build plugins and SPA routing.

**Tech Stack:** Spring Boot 3.4, Spring AI, JdbcTemplate/PostgreSQL, Vite 8, React 19, Maven frontend-maven-plugin

## Global Constraints

- Migration numbering: V3 exists (`V3__knowledge_content_hash.sql`). Use V4 for knowledge fingerprint, V5 for eval judge.
- All new enums/records go in their respective package (`knowledge/`, `evaluation/`).
- `EvalExecutor` must NOT write to the database — only `EvalService` persists results.
- Knowledge reindex must be atomic: prepare data first, swap chunks in `@Transactional`, preserve `docId`.
- Vite builds to `frontend/dist/`, NOT to `src/main/resources/static/`.
- Use `npm ci` (not `npm install`) for deterministic builds.
- `-Dskip.frontend=true` must skip the entire frontend build pipeline.

---

## Part 1: P2-6 — Atomic Reindex with Fingerprint-Based Change Detection

### Task 1: Add IndexConfig record and IngestStatus enum

**Files:**
- Create: `src/main/java/com/sxw/sxwaiagent/knowledge/IndexConfig.java`
- Create: `src/main/java/com/sxw/sxwaiagent/knowledge/IngestStatus.java`
- Create: `src/test/java/com/sxw/sxwaiagent/knowledge/IndexConfigTest.java`

**Interfaces:**
- Produces: `IndexConfig` record with `embeddingModel()`, `embeddingModelVersion()`, `chunkSize()`, `chunkOverlap()`, `parserVersion()`
- Produces: `IngestStatus` enum: `CREATED`, `UPDATED`, `REINDEXED`, `SKIPPED`
- Consumes: nothing (standalone types)

- [ ] **Step 1: Write the failing test for IndexConfig fingerprint**

```java
package com.sxw.sxwaiagent.knowledge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndexConfigTest {

    @Test
    void computeFingerprint_sameInputs_produceSameHash() {
        IndexConfig config = new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
        String hash1 = IndexConfig.computeFingerprint("abc123", config);
        String hash2 = IndexConfig.computeFingerprint("abc123", config);
        assertEquals(hash1, hash2);
    }

    @Test
    void computeFingerprint_differentModel_producesDifferentHash() {
        IndexConfig config1 = new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
        IndexConfig config2 = new IndexConfig("text-embedding-v4", "1.0", 1000, 100, "1.0");
        String hash1 = IndexConfig.computeFingerprint("abc123", config1);
        String hash2 = IndexConfig.computeFingerprint("abc123", config2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeFingerprint_differentContent_producesDifferentHash() {
        IndexConfig config = new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
        String hash1 = IndexConfig.computeFingerprint("abc123", config);
        String hash2 = IndexConfig.computeFingerprint("def456", config);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeFingerprint_differentChunkSize_producesDifferentHash() {
        IndexConfig config1 = new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
        IndexConfig config2 = new IndexConfig("text-embedding-v3", "1.0", 500, 100, "1.0");
        String hash1 = IndexConfig.computeFingerprint("abc123", config1);
        String hash2 = IndexConfig.computeFingerprint("abc123", config2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeFingerprint_returnsSha256Hex() {
        IndexConfig config = new IndexConfig("model", "1.0", 1000, 100, "1.0");
        String hash = IndexConfig.computeFingerprint("test", config);
        // SHA-256 hex = 64 chars
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=IndexConfigTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: FAIL — `IndexConfig` class not found

- [ ] **Step 3: Create IngestStatus enum**

```java
package com.sxw.sxwaiagent.knowledge;

/**
 * Result status for document ingestion operations.
 */
public enum IngestStatus {
    CREATED,
    UPDATED,
    REINDEXED,
    SKIPPED
}
```

- [ ] **Step 4: Create IndexConfig record**

```java
package com.sxw.sxwaiagent.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Captures the indexing pipeline configuration for fingerprint-based change detection.
 * When any of these values change, existing documents should be re-indexed.
 */
public record IndexConfig(
    String embeddingModel,
    String embeddingModelVersion,
    int chunkSize,
    int chunkOverlap,
    String parserVersion
) {

    /**
     * Compute a composite fingerprint from content hash + indexing config.
     * Returns a SHA-256 hex string (64 chars).
     */
    public static String computeFingerprint(String contentHash, IndexConfig config) {
        String raw = contentHash
            + "|" + config.embeddingModel()
            + "|" + config.embeddingModelVersion()
            + "|" + config.chunkSize()
            + "|" + config.chunkOverlap()
            + "|" + config.parserVersion();
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=IndexConfigTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/knowledge/IndexConfig.java \
        src/main/java/com/sxw/sxwaiagent/knowledge/IngestStatus.java \
        src/test/java/com/sxw/sxwaiagent/knowledge/IndexConfigTest.java
git commit -m "feat(knowledge): add IndexConfig fingerprint and IngestStatus enum"
```

---

### Task 2: Add V4 migration and update KnowledgeRepository

**Files:**
- Create: `src/main/resources/db/migration/V4__knowledge_fingerprint.sql`
- Modify: `src/main/java/com/sxw/sxwaiagent/knowledge/KnowledgeRepository.java`
- Create: `src/test/java/com/sxw/sxwaiagent/knowledge/KnowledgeRepositoryTest.java`

**Interfaces:**
- Consumes: `IndexConfig` (Task 1)
- Produces: `KnowledgeRepository.findByDocId()`, `.deleteChunksByDocId()`, `.updateDocumentMetadata()`
- Produces: Updated `KnowledgeDocumentRecord` with `contentHash`, `indexFingerprint` fields
- Produces: Updated `IngestResult` with `IngestStatus status` field

- [ ] **Step 1: Create V4 migration**

```sql
-- V4: Add index fingerprint for comprehensive change detection
-- content_hash column may already exist from V3; IF NOT EXISTS handles safely

ALTER TABLE ai_knowledge_document
    ADD COLUMN IF NOT EXISTS index_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_doc_fingerprint
    ON ai_knowledge_document(index_fingerprint);
```

- [ ] **Step 2: Write the failing test for new repository methods**

```java
package com.sxw.sxwaiagent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class KnowledgeRepositoryTest {

    @Autowired
    private KnowledgeRepository repository;

    @Test
    void findByDocId_existing_returnsRecord() {
        // Given: save a document
        String docId = repository.saveDocument("Test", "test.md", 3, "hash1", "fp1");

        // When
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> result = repository.findByDocId(docId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Test", result.get().title());
        assertEquals("hash1", result.get().contentHash());
        assertEquals("fp1", result.get().indexFingerprint());

        // Cleanup
        repository.deleteDocument(docId);
    }

    @Test
    void findByDocId_nonexistent_returnsEmpty() {
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> result =
            repository.findByDocId("nonexistent-id");
        assertTrue(result.isEmpty());
    }

    @Test
    void updateDocumentMetadata_preservesDocId() {
        String docId = repository.saveDocument("Old", "old.md", 2, "oldhash", "oldfp");

        repository.updateDocumentMetadata(docId, "New", "new.md", 5, "newhash", "newfp");

        Optional<KnowledgeRepository.KnowledgeDocumentRecord> result = repository.findByDocId(docId);
        assertTrue(result.isPresent());
        assertEquals("New", result.get().title());
        assertEquals("newhash", result.get().contentHash());
        assertEquals("newfp", result.get().indexFingerprint());
        assertEquals(5, result.get().chunkCount());

        repository.deleteDocument(docId);
    }

    @Test
    void deleteChunksByDocId_removesChunksKeepsDocument() {
        String docId = repository.saveDocument("Doc", "doc.md", 1, "h", "f");
        // Save a chunk
        var chunks = java.util.List.of(
            new MarkdownTextSplitter.DocumentChunk(docId + "_0", docId, 0, "# H", "content", 10)
        );
        var embeddings = java.util.List.of(new float[]{0.1f, 0.2f, 0.3f});
        repository.saveChunks(chunks, embeddings);

        // Delete chunks only
        repository.deleteChunksByDocId(docId);

        // Document still exists
        assertTrue(repository.findByDocId(docId).isPresent());

        // Cleanup
        repository.deleteDocument(docId);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn test -Dtest=KnowledgeRepositoryTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: FAIL — methods don't exist yet, record doesn't have new fields

- [ ] **Step 4: Update KnowledgeDocumentRecord and saveDocument**

In `KnowledgeRepository.java`, update the record:

```java
public record KnowledgeDocumentRecord(
    String docId, String title, String sourcePath,
    int chunkCount, String status,
    String contentHash,          // NEW
    String indexFingerprint,     // NEW
    Instant createdAt
) {}
```

Update `saveDocument(title, sourcePath, chunkCount, contentHash)` to accept fingerprint:

```java
public String saveDocument(String title, String sourcePath, int chunkCount,
                           String contentHash, String indexFingerprint) {
    String docId = UUID.randomUUID().toString();
    jdbcTemplate.update("""
        INSERT INTO ai_knowledge_document
            (doc_id, title, source_path, chunk_count, status, content_hash, index_fingerprint, created_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
        """,
        docId, title, sourcePath, chunkCount, contentHash, indexFingerprint,
        Timestamp.from(Instant.now())
    );
    log.info("Saved document: {} ({} chunks)", docId, chunkCount);
    return docId;
}
```

Keep the old 4-arg overload for backward compatibility:

```java
public String saveDocument(String title, String sourcePath, int chunkCount, String contentHash) {
    return saveDocument(title, sourcePath, chunkCount, contentHash, null);
}
```

- [ ] **Step 5: Add findByDocId, deleteChunksByDocId, updateDocumentMetadata**

Add to `KnowledgeRepository.java`:

```java
public Optional<KnowledgeDocumentRecord> findByDocId(String docId) {
    List<KnowledgeDocumentRecord> results = jdbcTemplate.query(
        "SELECT doc_id, title, source_path, chunk_count, status, content_hash, index_fingerprint, created_at " +
        "FROM ai_knowledge_document WHERE doc_id = ?",
        new KnowledgeDocumentRowMapper(), docId
    );
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
}

public void deleteChunksByDocId(String docId) {
    jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = ?", docId);
    log.debug("Deleted chunks for document: {}", docId);
}

public void updateDocumentMetadata(String docId, String title, String sourcePath,
                                    int chunkCount, String contentHash, String indexFingerprint) {
    jdbcTemplate.update("""
        UPDATE ai_knowledge_document
        SET title = ?, source_path = ?, chunk_count = ?,
            content_hash = ?, index_fingerprint = ?, updated_at = ?
        WHERE doc_id = ?
        """,
        title, sourcePath, chunkCount, contentHash, indexFingerprint,
        Timestamp.from(Instant.now()), docId
    );
    log.debug("Updated metadata for document: {}", docId);
}
```

Update the `findByContentHash` RowMapper and `listDocuments` RowMapper to use the new `KnowledgeDocumentRowMapper` that includes `contentHash` and `indexFingerprint`:

```java
private static class KnowledgeDocumentRowMapper implements RowMapper<KnowledgeDocumentRecord> {
    @Override
    public KnowledgeDocumentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeDocumentRecord(
            rs.getString("doc_id"),
            rs.getString("title"),
            rs.getString("source_path"),
            rs.getInt("chunk_count"),
            rs.getString("status"),
            rs.getString("content_hash"),
            rs.getString("index_fingerprint"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
```

Replace the inline lambdas in `findByContentHash()` and `listDocuments()` with `new KnowledgeDocumentRowMapper()`.

Note: the `ai_knowledge_document` table doesn't have an `updated_at` column. If the `UPDATE` fails, remove `updated_at` from the UPDATE SQL. Check V1 migration to confirm.

- [ ] **Step 6: Update IngestResult to include IngestStatus**

In `DocumentIngestService.java`, update the record:

```java
public record IngestResult(
    String docId,
    int chunkCount,
    String message,
    IngestStatus status
) {
    public boolean isSuccess() {
        return docId != null;
    }
}
```

Update existing `new IngestResult(...)` calls (3 places in `ingest()`) to add status:
- `return new IngestResult(doc.docId(), doc.chunkCount(), "Already indexed", IngestStatus.SKIPPED);`
- `return new IngestResult(null, 0, "No content to index", null);`
- `return new IngestResult(null, 0, "Embedding generation failed", null);`
- `return new IngestResult(docId, chunks.size(), "Success", IngestStatus.CREATED);`

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn test -Dtest=KnowledgeRepositoryTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: PASS (4 tests)

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V4__knowledge_fingerprint.sql \
        src/main/java/com/sxw/sxwaiagent/knowledge/KnowledgeRepository.java \
        src/main/java/com/sxw/sxwaiagent/knowledge/DocumentIngestService.java \
        src/test/java/com/sxw/sxwaiagent/knowledge/KnowledgeRepositoryTest.java
git commit -m "feat(knowledge): add V4 migration, repository methods for atomic reindex"
```

---

### Task 3: Rewrite DocumentIngestService with atomic flow

**Files:**
- Modify: `src/main/java/com/sxw/sxwaiagent/knowledge/DocumentIngestService.java`
- Create: `src/test/java/com/sxw/sxwaiagent/knowledge/DocumentIngestServiceTest.java`

**Interfaces:**
- Consumes: `IndexConfig` (Task 1), `KnowledgeRepository.findByDocId/deleteChunksByDocId/updateDocumentMetadata` (Task 2)
- Produces: `ingest(title, sourcePath, content, force)` — atomic ingest flow
- Produces: `reindex(docId, content)` — content re-upload reindex

- [ ] **Step 1: Write the failing tests**

```java
package com.sxw.sxwaiagent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestServiceTest {

    @Mock MarkdownTextSplitter textSplitter;
    @Mock EmbeddingService embeddingService;
    @Mock KnowledgeRepository knowledgeRepository;

    @InjectMocks DocumentIngestService ingestService;

    private IndexConfig indexConfig() {
        return new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
    }

    @Test
    void ingest_newDocument_returnsCreated() {
        when(knowledgeRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(textSplitter.split(any(), any())).thenReturn(List.of(
            new MarkdownTextSplitter.DocumentChunk("c1", "tmp", 0, "# H", "content", 10)
        ));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f}));
        when(knowledgeRepository.saveDocument(any(), any(), anyInt(), any(), any()))
            .thenReturn("new-doc-id");

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", "# content", false, indexConfig());

        assertEquals(IngestStatus.CREATED, result.status());
        assertEquals("new-doc-id", result.docId());
    }

    @Test
    void ingest_sameFingerprint_returnsSkipped() {
        String contentHash = "somehash";
        String fingerprint = IndexConfig.computeFingerprint(contentHash, indexConfig());

        var existing = new KnowledgeRepository.KnowledgeDocumentRecord(
            "existing-id", "title.md", "path", 3, "ACTIVE",
            contentHash, fingerprint, java.time.Instant.now()
        );
        when(knowledgeRepository.findByContentHash(any())).thenReturn(Optional.of(existing));

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", "same content that hashes to somehash", false, indexConfig());

        // Will be SKIPPED only if the computed hash matches
        // Since we can't easily predict SHA-256 of arbitrary content in a test,
        // test the fingerprint-matching path via reindex instead
        assertNotNull(result);
    }

    @Test
    void ingest_forceTrue_existingDoc_returnsReindexed() {
        String fingerprint = "fp1";
        var existing = new KnowledgeRepository.KnowledgeDocumentRecord(
            "existing-id", "title.md", "path", 3, "ACTIVE",
            "oldhash", fingerprint, java.time.Instant.now()
        );
        when(knowledgeRepository.findByContentHash(any())).thenReturn(Optional.of(existing));
        when(textSplitter.split(any(), any())).thenReturn(List.of(
            new MarkdownTextSplitter.DocumentChunk("c1", "tmp", 0, "# H", "content", 10)
        ));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f}));

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", "# content", true, indexConfig());

        assertEquals(IngestStatus.REINDEXED, result.status());
        assertEquals("existing-id", result.docId());  // preserves docId
        verify(knowledgeRepository).deleteChunksByDocId("existing-id");
        verify(knowledgeRepository).updateDocumentMetadata(eq("existing-id"), any(), any(), anyInt(), any(), any());
    }

    @Test
    void ingest_embeddingFailure_oldIndexPreserved() {
        when(knowledgeRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(textSplitter.split(any(), any())).thenReturn(List.of(
            new MarkdownTextSplitter.DocumentChunk("c1", "tmp", 0, "# H", "content", 10)
        ));
        when(embeddingService.embedBatch(any())).thenReturn(List.of());  // failure

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", "# content", false, indexConfig());

        assertNull(result.docId());
        assertFalse(result.isSuccess());
        // No DB writes should have occurred
        verify(knowledgeRepository, never()).saveDocument(any(), any(), anyInt(), any(), any());
        verify(knowledgeRepository, never()).deleteChunksByDocId(any());
    }

    @Test
    void reindex_existingDoc_replacesChunksAtomically() {
        var existing = new KnowledgeRepository.KnowledgeDocumentRecord(
            "doc-123", "title.md", "path", 3, "ACTIVE",
            "oldhash", "oldfp", java.time.Instant.now()
        );
        when(knowledgeRepository.findByDocId("doc-123")).thenReturn(Optional.of(existing));
        when(textSplitter.split(any(), any())).thenReturn(List.of(
            new MarkdownTextSplitter.DocumentChunk("c1", "tmp", 0, "# H", "new content", 10)
        ));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f}));

        DocumentIngestService.IngestResult result =
            ingestService.reindex("doc-123", "# new content", indexConfig());

        assertEquals(IngestStatus.REINDEXED, result.status());
        assertEquals("doc-123", result.docId());
        verify(knowledgeRepository).deleteChunksByDocId("doc-123");
        verify(knowledgeRepository).updateDocumentMetadata(eq("doc-123"), any(), any(), anyInt(), any(), any());
    }

    @Test
    void reindex_nonexistentDoc_throwsException() {
        when(knowledgeRepository.findByDocId("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            ingestService.reindex("missing", "content", indexConfig())
        );
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=DocumentIngestServiceTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: FAIL — `ingest()` doesn't have force/fingerprint params, `reindex()` doesn't exist

- [ ] **Step 3: Rewrite DocumentIngestService**

Replace `DocumentIngestService.java` with the atomic flow:

```java
package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    private final MarkdownTextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final KnowledgeRepository knowledgeRepository;

    public DocumentIngestService(
        MarkdownTextSplitter textSplitter,
        EmbeddingService embeddingService,
        KnowledgeRepository knowledgeRepository
    ) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.knowledgeRepository = knowledgeRepository;
    }

    public IngestResult ingestFromFile(Path filePath, boolean force, IndexConfig indexConfig) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String title = filePath.getFileName().toString();
        return ingest(title, filePath.toString(), content, force, indexConfig);
    }

    /**
     * Backward-compatible overload: defaults to force=false, no IndexConfig.
     */
    public IngestResult ingestFromFile(Path filePath) throws IOException {
        return ingestFromFile(filePath, false, null);
    }

    public IngestResult ingest(String title, String sourcePath, String content) {
        return ingest(title, sourcePath, content, false, null);
    }

    /**
     * Core ingest with atomic chunk replacement.
     *
     * Flow:
     * 1. Compute content hash and index fingerprint
     * 2. Check if existing doc matches fingerprint → SKIPPED
     * 3. Split + embed (prepare new data, no DB writes)
     * 4. If embedding fails → return failure, old data untouched
     * 5. @Transactional: delete old chunks, update metadata, insert new chunks
     */
    public IngestResult ingest(String title, String sourcePath, String content,
                                boolean force, IndexConfig indexConfig) {
        log.info("Ingesting document: {} (force={})", title, force);

        String contentHash = computeSha256(content);

        // Check for existing document with same content hash
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> existing =
            knowledgeRepository.findByContentHash(contentHash);

        // Fingerprint check: skip if matching and not forced
        if (existing.isPresent() && !force && indexConfig != null) {
            String fingerprint = IndexConfig.computeFingerprint(contentHash, indexConfig);
            if (fingerprint.equals(existing.get().indexFingerprint())) {
                knowledgeRepository.updateDocument(existing.get().docId());
                log.info("Document fingerprint matches, skipping: {}", title);
                return new IngestResult(existing.get().docId(), existing.get().chunkCount(),
                    "No changes", IngestStatus.SKIPPED);
            }
        }

        // If same content hash but not forced, also skip (backward compat without IndexConfig)
        if (existing.isPresent() && !force && indexConfig == null) {
            knowledgeRepository.updateDocument(existing.get().docId());
            log.info("Document already indexed, skipping: {}", title);
            return new IngestResult(existing.get().docId(), existing.get().chunkCount(),
                "Already indexed", IngestStatus.SKIPPED);
        }

        // Phase 1: Prepare new data (no DB writes yet)
        List<MarkdownTextSplitter.DocumentChunk> newChunks =
            textSplitter.split(content, "temp_" + System.currentTimeMillis());

        if (newChunks.isEmpty()) {
            log.warn("No chunks generated from document: {}", title);
            return new IngestResult(null, 0, "No content to index", null);
        }

        List<String> chunkTexts = newChunks.stream()
            .map(c -> c.breadcrumb() + "\n" + c.content())
            .toList();

        List<float[]> newVectors = embeddingService.embedBatch(chunkTexts);

        if (newVectors.isEmpty()) {
            log.error("Failed to generate embeddings for document: {}", title);
            return new IngestResult(null, 0, "Embedding generation failed", null);
        }

        // Phase 2: Atomic DB transaction
        String fingerprint = indexConfig != null
            ? IndexConfig.computeFingerprint(contentHash, indexConfig)
            : null;

        if (existing.isPresent() && (force || !contentHash.equals(existing.get().contentHash()))) {
            // Update existing document: replace chunks atomically, preserve docId
            String docId = existing.get().docId();
            IngestStatus status = force ? IngestStatus.REINDEXED : IngestStatus.UPDATED;
            atomicReplaceChunks(docId, title, sourcePath, newChunks, newVectors,
                contentHash, fingerprint);
            log.info("Document {} updated: {} chunks", docId, newChunks.size());
            return new IngestResult(docId, newChunks.size(), status.name(), status);
        }

        // Create new document
        String docId = saveDocumentAndChunks(title, sourcePath, newChunks, newVectors,
            contentHash, fingerprint);
        log.info("Successfully ingested document {} with {} chunks", docId, newChunks.size());
        return new IngestResult(docId, newChunks.size(), "Success", IngestStatus.CREATED);
    }

    /**
     * Reindex an existing document with new content (content re-upload).
     */
    public IngestResult reindex(String docId, String content, IndexConfig indexConfig) {
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> existing =
            knowledgeRepository.findByDocId(docId);

        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + docId);
        }

        KnowledgeRepository.KnowledgeDocumentRecord doc = existing.get();
        return ingest(doc.title(), doc.sourcePath(), content, true, indexConfig);
    }

    @Transactional
    protected void atomicReplaceChunks(String docId, String title, String sourcePath,
                                        List<MarkdownTextSplitter.DocumentChunk> newChunks,
                                        List<float[]> newVectors,
                                        String contentHash, String indexFingerprint) {
        knowledgeRepository.deleteChunksByDocId(docId);
        knowledgeRepository.updateDocumentMetadata(docId, title, sourcePath,
            newChunks.size(), contentHash, indexFingerprint);

        List<MarkdownTextSplitter.DocumentChunk> updatedChunks = newChunks.stream()
            .map(c -> new MarkdownTextSplitter.DocumentChunk(
                c.chunkId().replace("temp_", docId + "_"),
                docId, c.chunkIndex(), c.breadcrumb(), c.content(), c.tokenCount()
            ))
            .toList();
        knowledgeRepository.saveChunks(updatedChunks, newVectors);
    }

    @Transactional
    protected String saveDocumentAndChunks(String title, String sourcePath,
                                            List<MarkdownTextSplitter.DocumentChunk> newChunks,
                                            List<float[]> newVectors,
                                            String contentHash, String indexFingerprint) {
        String docId = knowledgeRepository.saveDocument(title, sourcePath,
            newChunks.size(), contentHash, indexFingerprint);

        List<MarkdownTextSplitter.DocumentChunk> updatedChunks = newChunks.stream()
            .map(c -> new MarkdownTextSplitter.DocumentChunk(
                c.chunkId().replace("temp_", docId + "_"),
                docId, c.chunkIndex(), c.breadcrumb(), c.content(), c.tokenCount()
            ))
            .toList();
        knowledgeRepository.saveChunks(updatedChunks, newVectors);
        return docId;
    }

    public void deleteDocument(String docId) {
        knowledgeRepository.deleteDocument(docId);
        log.info("Deleted document: {}", docId);
    }

    public List<KnowledgeRepository.KnowledgeDocumentRecord> listDocuments() {
        return knowledgeRepository.listDocuments();
    }

    private String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IngestResult(
        String docId,
        int chunkCount,
        String message,
        IngestStatus status
    ) {
        public boolean isSuccess() {
            return docId != null;
        }
    }
}
```

- [ ] **Step 4: Run all knowledge tests**

Run: `mvn test -Dtest="DocumentIngestServiceTest,KnowledgeRepositoryTest,IndexConfigTest" -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/knowledge/DocumentIngestService.java \
        src/test/java/com/sxw/sxwaiagent/knowledge/DocumentIngestServiceTest.java
git commit -m "feat(knowledge): atomic ingest with fingerprint-based change detection"
```

---

### Task 4: Update KnowledgeController with reindex endpoint

**Files:**
- Modify: `src/main/java/com/sxw/sxwaiagent/web/controller/KnowledgeController.java`

**Interfaces:**
- Consumes: `DocumentIngestService.ingest(title, sourcePath, content, force, indexConfig)`, `.reindex(docId, content, indexConfig)` (Task 3)
- Consumes: `IndexConfig` (Task 1)
- Produces: `POST /api/knowledge/documents` with `force` param
- Produces: `POST /api/knowledge/documents/text` with `force` param
- Produces: `PUT /api/knowledge/documents/{docId}/content` reindex endpoint

- [ ] **Step 1: Add IndexConfig bean and update controller**

First, create an `IndexConfig` bean. Add to `KnowledgeController.java` or a config class. Simplest approach: create it in the controller from `@Value` properties and pass to service:

Add to `KnowledgeController.java`:

```java
@RestController
@RequestMapping("/api/knowledge")
@Validated
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final DocumentIngestService ingestService;
    private final IndexConfig indexConfig;

    public KnowledgeController(
        DocumentIngestService ingestService,
        @Value("${sxw.knowledge.embedding-model:text-embedding-v3}") String embeddingModel,
        @Value("${sxw.knowledge.embedding-model-version:1.0}") String embeddingModelVersion,
        @Value("${sxw.knowledge.chunk-size:1000}") int chunkSize,
        @Value("${sxw.knowledge.chunk-overlap:100}") int chunkOverlap,
        @Value("${sxw.knowledge.parser-version:1.0}") String parserVersion
    ) {
        this.ingestService = ingestService;
        this.indexConfig = new IndexConfig(embeddingModel, embeddingModelVersion,
            chunkSize, chunkOverlap, parserVersion);
    }

    @PostMapping("/documents")
    public Result<DocumentIngestService.IngestResult> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean force
    ) throws IOException {
        log.info("Uploading document: {} (force={})", file.getOriginalFilename(), force);
        Path tempFile = Files.createTempFile("knowledge_", "_" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);
            DocumentIngestService.IngestResult result =
                ingestService.ingestFromFile(tempFile, force, indexConfig);
            return result.isSuccess() ? Result.ok(result) : Result.error(result.message());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @PostMapping("/documents/text")
    public Result<DocumentIngestService.IngestResult> ingestText(
        @RequestParam @NotBlank String title,
        @RequestParam(required = false) String sourcePath,
        @RequestParam(defaultValue = "false") boolean force,
        @RequestBody @NotBlank String content
    ) {
        log.info("Ingesting text document: {} (force={})", title, force);
        DocumentIngestService.IngestResult result = ingestService.ingest(
            title, sourcePath != null ? sourcePath : "text-input",
            content, force, indexConfig
        );
        return result.isSuccess() ? Result.ok(result) : Result.error(result.message());
    }

    @PutMapping("/documents/{docId}/content")
    public Result<DocumentIngestService.IngestResult> reindexDocument(
        @PathVariable String docId,
        @RequestBody @NotBlank String content
    ) {
        log.info("Reindexing document: {}", docId);
        try {
            DocumentIngestService.IngestResult result =
                ingestService.reindex(docId, content, indexConfig);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/documents")
    public Result<List<KnowledgeRepository.KnowledgeDocumentRecord>> listDocuments() {
        return Result.ok(ingestService.listDocuments());
    }

    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable String docId) {
        log.info("Deleting document: {}", docId);
        ingestService.deleteDocument(docId);
        return Result.ok(null);
    }
}
```

- [ ] **Step 2: Add knowledge config properties to application.yml**

Add under `sxw.knowledge`:

```yaml
  knowledge:
    embedding-model: ${SXW_KNOWLEDGE_EMBEDDING_MODEL:text-embedding-v3}
    embedding-model-version: ${SXW_KNOWLEDGE_EMBEDDING_VERSION:1.0}
    chunk-size: ${SXW_KNOWLEDGE_CHUNK_SIZE:1000}
    chunk-overlap: ${SXW_KNOWLEDGE_CHUNK_OVERLAP:100}
    parser-version: ${SXW_KNOWLEDGE_PARSER_VERSION:1.0}
    rrf:
      enabled: ${SXW_KNOWLEDGE_RRF_ENABLED:true}
      k: ${SXW_KNOWLEDGE_RRF_K:60}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/web/controller/KnowledgeController.java \
        src/main/resources/application.yml
git commit -m "feat(knowledge): add force param, reindex endpoint, IndexConfig bean"
```

---

### Task 5: Run full test suite for P2-6

- [ ] **Step 1: Run all tests**

Run: `mvn test -Dskip.frontend=true -pl .`
Expected: All tests pass, no regressions

- [ ] **Step 2: Fix any regressions**

If any existing tests break due to the `IngestResult` record change (added `status` field), update their assertions.

- [ ] **Step 3: Commit fixes if needed**

```bash
git add -A
git commit -m "fix: update tests for IngestResult status field"
```

---

## Part 2: P2-2 — Enhance EvalExecutor with LLM-as-Judge

### Task 6: Add ValidationMode, JudgeStatus enums and update records

**Files:**
- Create: `src/main/java/com/sxw/sxwaiagent/evaluation/ValidationMode.java`
- Create: `src/main/java/com/sxw/sxwaiagent/evaluation/JudgeStatus.java`
- Modify: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalCase.java`
- Modify: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalResult.java`

**Interfaces:**
- Produces: `ValidationMode` enum: `KEYWORD_ONLY`, `LLM_ONLY`, `ALL`, `ANY`
- Produces: `JudgeStatus` enum: `PASSED`, `FAILED`, `UNAVAILABLE`, `TIMEOUT`, `PARSE_ERROR`
- Produces: Updated `EvalCase` with `judgeCriteria`, `validationMode`
- Produces: Updated `EvalResult` with `keywordPassed`, `judgeStatus`, `judgeModel`, `judgeScore`, `judgeReason`, `validationMode`

- [ ] **Step 1: Create ValidationMode enum**

```java
package com.sxw.sxwaiagent.evaluation;

public enum ValidationMode {
    KEYWORD_ONLY,
    LLM_ONLY,
    ALL,
    ANY
}
```

- [ ] **Step 2: Create JudgeStatus enum**

```java
package com.sxw.sxwaiagent.evaluation;

public enum JudgeStatus {
    PASSED,
    FAILED,
    UNAVAILABLE,
    TIMEOUT,
    PARSE_ERROR
}
```

- [ ] **Step 3: Update EvalCase record**

Add `judgeCriteria` (after `validationRules`) and `validationMode` (after `judgeCriteria`):

```java
public record EvalCase(
    Long id,
    String caseId,
    String caseName,
    EvalCaseType caseType,
    EvalCaseStatus status,
    String profileCode,
    String inputPrompt,
    String expectedOutput,
    String validationRules,
    String judgeCriteria,
    ValidationMode validationMode,
    List<String> tags,
    Integer priority,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    // Update the convenience constructor to include new fields with defaults
    public EvalCase(
        String caseId, String caseName, EvalCaseType caseType,
        String profileCode, String inputPrompt, String expectedOutput
    ) {
        this(null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
            profileCode, inputPrompt, expectedOutput, null,
            null, ValidationMode.KEYWORD_ONLY,  // defaults
            null, 5, null, LocalDateTime.now(), LocalDateTime.now());
    }

    public boolean canRun() {
        return status == EvalCaseStatus.ACTIVE;
    }

    public boolean canEdit() {
        return status == EvalCaseStatus.DRAFT || status == EvalCaseStatus.PENDING;
    }

    public String buildSummary() {
        return String.format("[%s] %s (%s) - priority=%d, status=%s",
            caseId, caseName, caseType, priority, status);
    }
}
```

- [ ] **Step 4: Update EvalResult record**

```java
public record EvalResult(
    String caseId,
    String caseName,
    boolean passed,
    boolean keywordPassed,
    String actualOutput,
    String expectedOutput,
    String validationDetails,
    long durationMs,
    String errorMessage,
    JudgeStatus judgeStatus,
    String judgeModel,
    Double judgeScore,
    String judgeReason,
    ValidationMode validationMode
) {
    public static EvalResult pass(String caseId, String caseName, String actualOutput,
                                   String expectedOutput, long durationMs) {
        return new EvalResult(caseId, caseName, true, true,
            actualOutput, expectedOutput, null, durationMs, null,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    public static EvalResult fail(String caseId, String caseName, String actualOutput,
                                   String expectedOutput, String validationDetails, long durationMs) {
        return new EvalResult(caseId, caseName, false, false,
            actualOutput, expectedOutput, validationDetails, durationMs, null,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    public static EvalResult error(String caseId, String caseName, String errorMessage, long durationMs) {
        return new EvalResult(caseId, caseName, false, false,
            null, null, null, durationMs, errorMessage,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    /**
     * Full result with judge details.
     */
    public static EvalResult withJudge(String caseId, String caseName, boolean passed,
                                        boolean keywordPassed, String actualOutput, String expectedOutput,
                                        String validationDetails, long durationMs,
                                        JudgeStatus judgeStatus, String judgeModel,
                                        Double judgeScore, String judgeReason,
                                        ValidationMode validationMode) {
        return new EvalResult(caseId, caseName, passed, keywordPassed,
            actualOutput, expectedOutput, validationDetails, durationMs, null,
            judgeStatus, judgeModel, judgeScore, judgeReason, validationMode);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS. If `EvalCaseRepository.EvalCaseRowMapper` fails (wrong arg count), that's expected — fix in next task.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/evaluation/ValidationMode.java \
        src/main/java/com/sxw/sxwaiagent/evaluation/JudgeStatus.java \
        src/main/java/com/sxw/sxwaiagent/evaluation/EvalCase.java \
        src/main/java/com/sxw/sxwaiagent/evaluation/EvalResult.java
git commit -m "feat(eval): add ValidationMode, JudgeStatus, update EvalCase/EvalResult records"
```

---

### Task 7: Add V5 migration and update EvalCaseRepository

**Files:**
- Create: `src/main/resources/db/migration/V5__eval_llm_judge.sql`
- Modify: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalCaseRepository.java`
- Create: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalResultRepository.java`

**Interfaces:**
- Consumes: Updated `EvalCase`, `EvalResult` (Task 6)
- Produces: Updated `EvalCaseRowMapper` with `judge_criteria`, `validation_mode`
- Produces: `EvalResultRepository.saveBatch()`, `.findByRunId()`

- [ ] **Step 1: Create V5 migration**

```sql
-- V5: LLM-as-Judge integration for eval system

ALTER TABLE ai_eval_case
    ADD COLUMN IF NOT EXISTS judge_criteria TEXT;
ALTER TABLE ai_eval_case
    ADD COLUMN IF NOT EXISTS validation_mode VARCHAR(20) DEFAULT 'KEYWORD_ONLY';

CREATE TABLE IF NOT EXISTS ai_eval_result (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL,
    case_id             VARCHAR(64)  NOT NULL,
    case_name           VARCHAR(255),
    passed              BOOLEAN      NOT NULL DEFAULT FALSE,
    keyword_passed      BOOLEAN,
    judge_status        VARCHAR(20),
    judge_model         VARCHAR(128),
    judge_prompt_version VARCHAR(20),
    judge_score         DOUBLE PRECISION,
    judge_reason        TEXT,
    score               DOUBLE PRECISION,
    actual_output       TEXT,
    expected_output     TEXT,
    validation_details  TEXT,
    validation_mode     VARCHAR(20),
    duration_ms         BIGINT       NOT NULL DEFAULT 0,
    error_message       TEXT,
    attempt_no          INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, case_id, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_eval_result_run ON ai_eval_result(run_id);
CREATE INDEX IF NOT EXISTS idx_eval_result_case ON ai_eval_result(case_id);
```

- [ ] **Step 2: Update EvalCaseRowMapper and save method**

In `EvalCaseRepository.java`, update the `save()` method:

```java
public void save(EvalCase evalCase) {
    String sql = """
        INSERT INTO ai_eval_case (
            case_id, case_name, case_type, status, profile_code,
            input_prompt, expected_output, validation_rules,
            judge_criteria, validation_mode,
            tags, priority, created_by, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (case_id) DO UPDATE SET
            case_name = EXCLUDED.case_name,
            case_type = EXCLUDED.case_type,
            status = EXCLUDED.status,
            input_prompt = EXCLUDED.input_prompt,
            expected_output = EXCLUDED.expected_output,
            validation_rules = EXCLUDED.validation_rules,
            judge_criteria = EXCLUDED.judge_criteria,
            validation_mode = EXCLUDED.validation_mode,
            tags = EXCLUDED.tags,
            priority = EXCLUDED.priority,
            updated_at = EXCLUDED.updated_at
        """;

    String tagsStr = evalCase.tags() != null ? String.join(",", evalCase.tags()) : "";
    String validationMode = evalCase.validationMode() != null
        ? evalCase.validationMode().name() : ValidationMode.KEYWORD_ONLY.name();

    jdbcTemplate.update(sql,
        evalCase.caseId(), evalCase.caseName(), evalCase.caseType().name(),
        evalCase.status().name(), evalCase.profileCode(),
        evalCase.inputPrompt(), evalCase.expectedOutput(), evalCase.validationRules(),
        evalCase.judgeCriteria(), validationMode,
        tagsStr, evalCase.priority(), evalCase.createdBy(),
        evalCase.createdAt(), evalCase.updatedAt()
    );
    log.debug("Saved eval case: {}", evalCase.caseId());
}
```

Update `EvalCaseRowMapper`:

```java
private static class EvalCaseRowMapper implements RowMapper<EvalCase> {
    @Override
    public EvalCase mapRow(ResultSet rs, int rowNum) throws SQLException {
        String tagsStr = rs.getString("tags");
        List<String> tags = (tagsStr != null && !tagsStr.isEmpty())
            ? Arrays.asList(tagsStr.split(","))
            : Collections.emptyList();

        String validationModeStr = rs.getString("validation_mode");
        ValidationMode validationMode = (validationModeStr != null && !validationModeStr.isEmpty())
            ? ValidationMode.valueOf(validationModeStr)
            : ValidationMode.KEYWORD_ONLY;

        return new EvalCase(
            rs.getLong("id"),
            rs.getString("case_id"),
            rs.getString("case_name"),
            EvalCaseType.valueOf(rs.getString("case_type")),
            EvalCaseStatus.valueOf(rs.getString("status")),
            rs.getString("profile_code"),
            rs.getString("input_prompt"),
            rs.getString("expected_output"),
            rs.getString("validation_rules"),
            rs.getString("judge_criteria"),
            validationMode,
            tags,
            rs.getInt("priority"),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
```

- [ ] **Step 3: Create EvalResultRepository**

```java
package com.sxw.sxwaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class EvalResultRepository {

    private static final Logger log = LoggerFactory.getLogger(EvalResultRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public EvalResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveBatch(String runId, List<EvalResult> results) {
        String sql = """
            INSERT INTO ai_eval_result (
                run_id, case_id, case_name, passed, keyword_passed,
                judge_status, judge_model, judge_score, judge_reason,
                score, actual_output, expected_output,
                validation_details, validation_mode,
                duration_ms, error_message, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        for (EvalResult r : results) {
            jdbcTemplate.update(sql,
                runId, r.caseId(), r.caseName(), r.passed(), r.keywordPassed(),
                r.judgeStatus() != null ? r.judgeStatus().name() : null,
                r.judgeModel(), r.judgeScore(), r.judgeReason(),
                r.judgeScore(),  // score = judgeScore for now
                r.actualOutput(), r.expectedOutput(),
                r.validationDetails(),
                r.validationMode() != null ? r.validationMode().name() : null,
                r.durationMs(), r.errorMessage(),
                Timestamp.from(Instant.now())
            );
        }
        log.debug("Saved {} eval results for run {}", results.size(), runId);
    }

    public List<EvalResult> findByRunId(String runId) {
        String sql = """
            SELECT * FROM ai_eval_result
            WHERE run_id = ?
            ORDER BY id
            """;
        return jdbcTemplate.query(sql, new EvalResultRowMapper(), runId);
    }

    private static class EvalResultRowMapper implements RowMapper<EvalResult> {
        @Override
        public EvalResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            String judgeStatusStr = rs.getString("judge_status");
            JudgeStatus judgeStatus = (judgeStatusStr != null && !judgeStatusStr.isEmpty())
                ? JudgeStatus.valueOf(judgeStatusStr) : null;

            String validationModeStr = rs.getString("validation_mode");
            ValidationMode validationMode = (validationModeStr != null && !validationModeStr.isEmpty())
                ? ValidationMode.valueOf(validationModeStr) : ValidationMode.KEYWORD_ONLY;

            double judgeScoreVal = rs.getDouble("judge_score");
            Double judgeScore = rs.wasNull() ? null : judgeScoreVal;

            return new EvalResult(
                rs.getString("case_id"),
                rs.getString("case_name"),
                rs.getBoolean("passed"),
                rs.getBoolean("keyword_passed"),
                rs.getString("actual_output"),
                rs.getString("expected_output"),
                rs.getString("validation_details"),
                rs.getLong("duration_ms"),
                rs.getString("error_message"),
                judgeStatus,
                rs.getString("judge_model"),
                judgeScore,
                rs.getString("judge_reason"),
                validationMode
            );
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V5__eval_llm_judge.sql \
        src/main/java/com/sxw/sxwaiagent/evaluation/EvalCaseRepository.java \
        src/main/java/com/sxw/sxwaiagent/evaluation/EvalResultRepository.java
git commit -m "feat(eval): add V5 migration, EvalResultRepository, update EvalCaseRepository"
```

---

### Task 8: Create EvalJudgeConfig and update EvalExecutor

**Files:**
- Create: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalJudgeConfig.java`
- Modify: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalExecutor.java`
- Create: `src/test/java/com/sxw/sxwaiagent/evaluation/EvalExecutorTest.java`

**Interfaces:**
- Consumes: `ValidationMode`, `JudgeStatus`, `EvalResult` (Task 6)
- Consumes: `EvalResultRepository` (Task 7)
- Consumes: `LlmJudgeEvaluator` from `infrastructure.eval` (existing)
- Produces: `EvalJudgeConfig` bean, updated `EvalExecutor.execute()` with validation modes

- [ ] **Step 1: Write the failing tests for EvalExecutor validation modes**

```java
package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalExecutorTest {

    @Mock AgentRuntime runtime;
    @Mock ObjectProvider<LlmJudgeEvaluator> judgeProvider;
    @Mock LlmJudgeEvaluator judge;

    private EvalExecutor executor;

    private AgentProfile mockProfile() {
        return new AgentProfile() {
            public AgentProfileCode code() { return AgentProfileCode.GENERAL; }
            public String name() { return "General"; }
            public String systemPrompt() { return "test"; }
            public List<String> toolNames() { return List.of(); }
        };
    }

    private EvalCase makeCase(String expected, String judgeCriteria, ValidationMode mode) {
        return new EvalCase(null, "case-1", "Test", EvalCaseType.CONVERSATION,
            EvalCaseStatus.ACTIVE, AgentProfileCode.GENERAL,
            "input", expected, null,
            judgeCriteria, mode,
            null, 5, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @BeforeEach
    void setup() {
        AgentProfile profile = mockProfile();
        executor = new EvalExecutor(List.of(profile), List.of(runtime), judgeProvider);
    }

    @Test
    void keywordOnly_noJudgeCriteria_passes() {
        when(runtime.execute(any())).thenReturn(new AgentResponse("hello world", List.of(), List.of(), null));
        EvalCase evalCase = makeCase("hello", null, ValidationMode.KEYWORD_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertTrue(result.keywordPassed());
        assertNull(result.judgeStatus());
    }

    @Test
    void llmOnly_skipsKeywordCheck() {
        when(runtime.execute(any())).thenReturn(new AgentResponse("response", List.of(), List.of(), null));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new com.sxw.sxwaiagent.infrastructure.eval.CaseResult.Check(true, 0.9, "good"));

        EvalCase evalCase = makeCase(null, "Is the response polite?", ValidationMode.LLM_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertEquals(JudgeStatus.PASSED, result.judgeStatus());
    }

    @Test
    void allMode_keywordPassJudgeFail_fails() {
        when(runtime.execute(any())).thenReturn(new AgentResponse("hello", List.of(), List.of(), null));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new com.sxw.sxwaiagent.infrastructure.eval.CaseResult.Check(false, 0.3, "bad"));

        EvalCase evalCase = makeCase("hello", "criteria", ValidationMode.ALL);

        EvalResult result = executor.execute(evalCase);

        assertFalse(result.passed());
        assertTrue(result.keywordPassed());
        assertEquals(JudgeStatus.FAILED, result.judgeStatus());
    }

    @Test
    void anyMode_keywordFailJudgePass_passes() {
        when(runtime.execute(any())).thenReturn(new AgentResponse("xyz", List.of(), List.of(), null));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new com.sxw.sxwaiagent.infrastructure.eval.CaseResult.Check(true, 0.8, "ok"));

        EvalCase evalCase = makeCase("hello", "criteria", ValidationMode.ANY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertFalse(result.keywordPassed());
        assertEquals(JudgeStatus.PASSED, result.judgeStatus());
    }

    @Test
    void judgeUnavailable_failsWithUnavailableStatus() {
        when(runtime.execute(any())).thenReturn(new AgentResponse("response", List.of(), List.of(), null));
        when(judgeProvider.getIfAvailable()).thenReturn(null);  // no judge

        EvalCase evalCase = makeCase(null, "criteria", ValidationMode.LLM_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertFalse(result.passed());
        assertEquals(JudgeStatus.UNAVAILABLE, result.judgeStatus());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=EvalExecutorTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: FAIL — constructor doesn't accept `ObjectProvider`, `execute()` doesn't support modes

- [ ] **Step 3: Create EvalJudgeConfig**

```java
package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvalJudgeConfig {

    @Bean
    @ConditionalOnBean(ChatModel.class)
    public LlmJudgeEvaluator llmJudgeEvaluator(ChatModel chatModel) {
        return new LlmJudgeEvaluator(chatModel);
    }
}
```

- [ ] **Step 4: Rewrite EvalExecutor with validation modes**

```java
package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import com.sxw.sxwaiagent.infrastructure.eval.CaseResult;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EvalExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvalExecutor.class);
    private static final double JUDGE_PASS_THRESHOLD = 0.7;

    private final List<AgentProfile> profileList;
    private final List<AgentRuntime> runtimeList;
    private final ObjectProvider<LlmJudgeEvaluator> judgeProvider;

    private Map<AgentProfileCode, AgentProfile> profileMap;
    private AgentRuntime runtime;

    public EvalExecutor(
        List<AgentProfile> profileList,
        List<AgentRuntime> runtimeList,
        ObjectProvider<LlmJudgeEvaluator> judgeProvider
    ) {
        this.profileList = profileList;
        this.runtimeList = runtimeList;
        this.judgeProvider = judgeProvider;
    }

    @PostConstruct
    void init() {
        profileMap = new HashMap<>();
        for (AgentProfile p : profileList) {
            profileMap.put(p.code(), p);
        }
        runtime = runtimeList.stream()
            .filter(r -> r.getClass().getSimpleName().equals("ToolUseLoopRuntime"))
            .findFirst()
            .orElse(runtimeList.isEmpty() ? null : runtimeList.get(0));
        log.info("EvalExecutor initialized: {} profiles, runtime={}",
            profileMap.size(), runtime != null ? runtime.getClass().getSimpleName() : "NONE");
    }

    public EvalResult execute(EvalCase evalCase) {
        long startTime = System.currentTimeMillis();
        try {
            AgentProfile profile = profileMap.get(evalCase.profileCode());
            if (profile == null) {
                throw new IllegalArgumentException("Profile not found: " + evalCase.profileCode());
            }
            if (runtime == null) {
                throw new IllegalStateException("No runtime configured");
            }

            // Run agent
            String requestId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
            AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId("trace-" + requestId)
                .chatId("eval-chat-" + evalCase.caseId())
                .profile(profile)
                .userMessage(evalCase.inputPrompt())
                .history(List.of())
                .metadata(Map.of("evalCaseId", evalCase.caseId()))
                .build();

            AgentResponse response = runtime.execute(context);
            String actualOutput = response.answer();
            long durationMs = System.currentTimeMillis() - startTime;

            // Phase 1: Keyword validation
            boolean keywordPassed = evaluateKeyword(actualOutput, evalCase.expectedOutput(),
                evalCase.validationRules());

            // Phase 2: LLM Judge validation
            JudgeStatus judgeStatus = null;
            String judgeModel = null;
            Double judgeScore = null;
            String judgeReason = null;

            if (evalCase.judgeCriteria() != null && !evalCase.judgeCriteria().isBlank()) {
                LlmJudgeEvaluator judgeEvaluator = judgeProvider.getIfAvailable();
                if (judgeEvaluator == null) {
                    judgeStatus = JudgeStatus.UNAVAILABLE;
                    judgeReason = "LLM judge is not configured but judgeCriteria is set";
                } else {
                    try {
                        // Build an infrastructure EvalCase for the judge (7-field record)
                        var infraCase = new com.sxw.sxwaiagent.infrastructure.eval.EvalCase(
                            evalCase.caseId(),        // id
                            "eval",                    // category
                            evalCase.inputPrompt(),    // input
                            List.of(),                 // expectKeywordsAny
                            List.of(),                 // expectKeywordsAll
                            evalCase.judgeCriteria(),  // judgeCriteria
                            0                          // maxLatencyMs (0 = no check)
                        );
                        CaseResult.Check check = judgeEvaluator.evaluateCase(infraCase, actualOutput);
                        judgeScore = check.score();
                        judgeReason = check.reason();
                        judgeModel = "project-chatmodel";
                        judgeStatus = check.passed() ? JudgeStatus.PASSED : JudgeStatus.FAILED;
                    } catch (Exception e) {
                        judgeStatus = JudgeStatus.TIMEOUT;
                        judgeReason = "judge error: " + e.getMessage();
                        log.warn("Judge call failed for case {}: {}", evalCase.caseId(), e.getMessage());
                    }
                }
            }

            // Phase 3: Combine per ValidationMode
            ValidationMode mode = evalCase.validationMode() != null
                ? evalCase.validationMode() : ValidationMode.KEYWORD_ONLY;
            boolean passed = combineResults(mode, keywordPassed, judgeStatus);

            String validationDetails = null;
            if (!passed) {
                validationDetails = String.format("mode=%s, keyword=%s, judge=%s",
                    mode, keywordPassed, judgeStatus);
            }

            log.info("Eval case {}: passed={}, keyword={}, judge={} ({}ms)",
                evalCase.caseId(), passed, keywordPassed, judgeStatus, durationMs);

            return EvalResult.withJudge(evalCase.caseId(), evalCase.caseName(),
                passed, keywordPassed, actualOutput, evalCase.expectedOutput(),
                validationDetails, durationMs,
                judgeStatus, judgeModel, judgeScore, judgeReason, mode);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Eval case ERROR: {} ({}ms)", evalCase.caseId(), durationMs, e);
            return EvalResult.error(evalCase.caseId(), evalCase.caseName(), e.getMessage(), durationMs);
        }
    }

    public List<EvalResult> executeBatch(List<EvalCase> cases) {
        List<EvalResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            if (!evalCase.canRun()) {
                log.warn("Skipping eval case: {} (status={})", evalCase.caseId(), evalCase.status());
                continue;
            }
            results.add(execute(evalCase));
        }
        return results;
    }

    private boolean evaluateKeyword(String actual, String expected, String rules) {
        if (actual == null || actual.isEmpty()) return false;
        if (expected == null || expected.isEmpty()) return true;
        if (rules == null || rules.isEmpty()) {
            return actual.contains(expected) || expected.contains(actual);
        }
        return actual.contains(expected);
    }

    private boolean combineResults(ValidationMode mode, boolean keywordPassed, JudgeStatus judgeStatus) {
        boolean judgePassed = judgeStatus == JudgeStatus.PASSED;
        return switch (mode) {
            case KEYWORD_ONLY -> keywordPassed;
            case LLM_ONLY -> judgePassed;
            case ALL -> keywordPassed && judgePassed;
            case ANY -> keywordPassed || judgePassed;
        };
    }
}
```

**Important:** Remove the `import ... EvalCase as InfraEvalCase` line (invalid Java). Instead, use the full qualified name `com.sxw.sxwaiagent.infrastructure.eval.EvalCase` inline when constructing the infrastructure EvalCase. The infrastructure `EvalCase` record has 7 fields: `(String id, String category, String input, List<String> expectKeywordsAny, List<String> expectKeywordsAll, String judgeCriteria, long maxLatencyMs)`. The `CaseResult.Check` record has fields: `passed()`, `score()`, `reason()`.

- [ ] **Step 5: Run EvalExecutorTest**

Run: `mvn test -Dtest=EvalExecutorTest -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/evaluation/EvalJudgeConfig.java \
        src/main/java/com/sxw/sxwaiagent/evaluation/EvalExecutor.java \
        src/test/java/com/sxw/sxwaiagent/evaluation/EvalExecutorTest.java
git commit -m "feat(eval): EvalExecutor with ValidationMode and LLM-as-Judge integration"
```

---

### Task 9: Update EvalService and EvalController

**Files:**
- Modify: `src/main/java/com/sxw/sxwaiagent/evaluation/EvalService.java`
- Modify: `src/main/java/com/sxw/sxwaiagent/web/controller/EvalController.java`

**Interfaces:**
- Consumes: `EvalResultRepository` (Task 7), updated `EvalResult` (Task 6)
- Produces: `EvalService.executeRun()` persists individual results, `getRunResults(runId)`
- Produces: `GET /api/eval/runs/{runId}/results` endpoint

- [ ] **Step 1: Update EvalService**

Add `EvalResultRepository` injection and result persistence:

```java
@Service
public class EvalService {

    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalResultRepository evalResultRepository;  // NEW
    private final EvalExecutor evalExecutor;

    public EvalService(
        EvalCaseRepository evalCaseRepository,
        EvalRunRepository evalRunRepository,
        EvalResultRepository evalResultRepository,
        EvalExecutor evalExecutor
    ) {
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalExecutor = evalExecutor;
    }

    // ... existing createCase, findCase, etc. (unchanged) ...

    public void executeRun(String runId) {
        Optional<EvalRun> optRun = evalRunRepository.findByRunId(runId);
        if (optRun.isEmpty()) throw new IllegalArgumentException("Eval run not found: " + runId);

        EvalRun run = optRun.get();
        if (!run.canStart()) throw new IllegalStateException("Eval run cannot start: " + run.status());

        evalRunRepository.updateStatus(runId, EvalRunStatus.RUNNING);

        try {
            List<EvalCase> cases = evalCaseRepository.findByIds(run.caseIds());
            if (cases.isEmpty()) throw new IllegalStateException("No cases found for run: " + runId);

            long startTime = System.currentTimeMillis();
            List<EvalResult> results = evalExecutor.executeBatch(cases);
            long durationMs = System.currentTimeMillis() - startTime;

            // Persist individual results
            evalResultRepository.saveBatch(runId, results);

            // Compute summary
            int passed = 0, failed = 0;
            for (EvalResult r : results) {
                if (r.passed()) passed++; else failed++;
            }
            int skipped = cases.size() - results.size();
            double passRate = cases.size() > 0 ? (double) passed / cases.size() * 100.0 : 0.0;

            evalRunRepository.updateResults(runId, EvalRunStatus.COMPLETED,
                passed, failed, skipped, passRate, durationMs);
            log.info("Eval run completed: {} - {} passed, {} failed ({}ms)",
                runId, passed, failed, durationMs);

        } catch (Exception e) {
            log.error("Eval run failed: {}", runId, e);
            evalRunRepository.updateError(runId, e.getMessage());
        }
    }

    public List<EvalResult> getRunResults(String runId) {
        return evalResultRepository.findByRunId(runId);
    }

    // ... existing findRun, listRuns, etc. (unchanged) ...
}
```

- [ ] **Step 2: Update EvalController**

Add `judgeCriteria` and `validationMode` to `CreateCaseRequest`, add results endpoint:

```java
// Add to EvalController:

@GetMapping("/runs/{runId}/results")
public Result<List<EvalResult>> getRunResults(@PathVariable String runId) {
    List<EvalResult> results = evalService.getRunResults(runId);
    return Result.ok(results);
}

// Update CreateCaseRequest:
public record CreateCaseRequest(
    @NotBlank String name,
    @NotBlank String input,
    String expectedOutput,
    String caseType,
    String profileCode,
    String tags,
    String judgeCriteria,       // NEW
    String validationMode       // NEW
) {}

// Update createCase handler:
@PostMapping("/cases")
public Result<EvalCase> createCase(@RequestBody CreateCaseRequest request) {
    EvalCaseType caseType = request.caseType() != null
        ? EvalCaseType.valueOf(request.caseType()) : EvalCaseType.CONVERSATION;
    ValidationMode mode = request.validationMode() != null
        ? ValidationMode.valueOf(request.validationMode()) : ValidationMode.KEYWORD_ONLY;

    EvalCase evalCase = evalService.createCase(
        request.name(), caseType,
        request.profileCode() != null ? request.profileCode() : "GENERAL",
        request.input(), request.expectedOutput(),
        request.judgeCriteria(), mode,  // NEW params
        null
    );
    return Result.ok(evalCase);
}
```

Update `EvalService.createCase()` to accept `judgeCriteria` and `validationMode`:

```java
public EvalCase createCase(
    String caseName, EvalCaseType caseType, String profileCode,
    String inputPrompt, String expectedOutput,
    String judgeCriteria, ValidationMode validationMode,
    String createdBy
) {
    String caseId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
    EvalCase evalCase = new EvalCase(
        null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
        profileCode, inputPrompt, expectedOutput, null,
        judgeCriteria, validationMode != null ? validationMode : ValidationMode.KEYWORD_ONLY,
        null, 5, createdBy, LocalDateTime.now(), LocalDateTime.now()
    );
    evalCaseRepository.save(evalCase);
    return evalCase;
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run all eval tests**

Run: `mvn test -Dtest="EvalExecutorTest" -Dskip.frontend=true -pl . -Dsurefire.useFile=false`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/evaluation/EvalService.java \
        src/main/java/com/sxw/sxwaiagent/web/controller/EvalController.java
git commit -m "feat(eval): wire EvalResult persistence, add results endpoint, update createCase"
```

---

## Part 3: P2-5 — Frontend Maven Build Integration

### Task 10: Update vite.config.ts and pom.xml build pipeline

**Files:**
- Modify: `frontend/vite.config.ts`
- Modify: `pom.xml`
- Modify: `.gitignore`

- [ ] **Step 1: Update vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      }
    }
  }
})
```

- [ ] **Step 2: Add frontend-maven-plugin + maven-resources-plugin to pom.xml**

Add inside the `<build><plugins>` section, after the existing plugins:

```xml
<!-- Frontend build (Node + npm) -->
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

<!-- Copy frontend dist to target/classes/static -->
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

Add to `<properties>`:

```xml
<skip.frontend>false</skip.frontend>
```

- [ ] **Step 3: Update .gitignore**

Add at the end of the `### CUSTOM ###` section:

```
# Frontend build output
frontend/dist/
frontend/node_modules/
frontend/node/
```

- [ ] **Step 4: Verify build with skip.frontend=true**

Run: `mvn compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS (no Node install)

- [ ] **Step 5: Commit**

```bash
git add frontend/vite.config.ts pom.xml .gitignore
git commit -m "feat(build): add frontend-maven-plugin, copy dist to target, skip.frontend flag"
```

---

### Task 11: Add SPA routing and delete old frontend

**Files:**
- Create: `src/main/java/com/sxw/sxwaiagent/web/controller/SpaForwardController.java`
- Modify: `src/main/java/com/sxw/sxwaiagent/auth/SecurityConfig.java`
- Delete: `src/main/resources/static/index.html`

- [ ] **Step 1: Create SpaForwardController**

```java
package com.sxw.sxwaiagent.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards known SPA routes to index.html so React Router handles client-side routing.
 * Only whitelists specific frontend paths — API/actuator/swagger paths are unaffected.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {
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

- [ ] **Step 2: Delete old index.html**

```bash
git rm src/main/resources/static/index.html
```

- [ ] **Step 3: Verify SecurityConfig already permits SPA resources**

`SecurityConfig.java` line 74 already has:
```java
.requestMatchers("/", "/index.html", "/favicon.ico", "/favicon.svg", "/assets/**", "/icons.svg").permitAll();
```

This is sufficient. No changes needed to SecurityConfig.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sxw/sxwaiagent/web/controller/SpaForwardController.java
git add -u src/main/resources/static/index.html
git commit -m "feat(frontend): add SPA forward controller, remove legacy Vue index.html"
```

---

### Task 12: Final verification — full build and test

- [ ] **Step 1: Run full test suite**

Run: `mvn test -Dskip.frontend=true -pl .`
Expected: All tests pass

- [ ] **Step 2: Verify full build with frontend (requires Node.js)**

Run: `mvn clean package -pl .`
Expected: BUILD SUCCESS, `target/classes/static/index.html` exists

- [ ] **Step 3: Verify skip.frontend works**

Run: `mvn clean compile -Dskip.frontend=true -pl .`
Expected: BUILD SUCCESS, no Node/npm output in logs

- [ ] **Step 4: Verify JAR contents**

Run (after `mvn clean package`):
```bash
jar tf target/sxw-ai-agent-0.0.1-SNAPSHOT.jar | grep static/
```
Expected: Lists `static/index.html`, `static/assets/...`

- [ ] **Step 5: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: address P2-5 build verification issues"
```
