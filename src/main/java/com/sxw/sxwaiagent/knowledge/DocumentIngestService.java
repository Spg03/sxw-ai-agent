package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    public DocumentIngestService(
        MarkdownTextSplitter textSplitter,
        EmbeddingService embeddingService,
        KnowledgeRepository knowledgeRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.knowledgeRepository = knowledgeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        boolean fingerprintChanged = existing.isPresent()
            && indexConfig != null
            && existing.get().indexFingerprint() != null
            && fingerprint != null
            && !fingerprint.equals(existing.get().indexFingerprint());

        if (existing.isPresent() && (force || !contentHash.equals(existing.get().contentHash()) || fingerprintChanged)) {
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

    protected void atomicReplaceChunks(String docId, String title, String sourcePath,
                                        List<MarkdownTextSplitter.DocumentChunk> newChunks,
                                        List<float[]> newVectors,
                                        String contentHash, String indexFingerprint) {
        transactionTemplate.executeWithoutResult(status -> {
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
        });
    }

    protected String saveDocumentAndChunks(String title, String sourcePath,
                                            List<MarkdownTextSplitter.DocumentChunk> newChunks,
                                            List<float[]> newVectors,
                                            String contentHash, String indexFingerprint) {
        return transactionTemplate.execute(status -> {
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
        });
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
