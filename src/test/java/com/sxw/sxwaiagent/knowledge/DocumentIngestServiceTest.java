package com.sxw.sxwaiagent.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestServiceTest {

    @Mock MarkdownTextSplitter textSplitter;
    @Mock EmbeddingService embeddingService;
    @Mock KnowledgeRepository knowledgeRepository;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks DocumentIngestService ingestService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any()))
            .thenReturn(new SimpleTransactionStatus());
    }

    private IndexConfig indexConfig() {
        return new IndexConfig("text-embedding-v3", "1.0", 1000, 100, "1.0");
    }

    private static String sha256(String input) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
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
        String content = "same content that hashes to somehash";
        String actualHash = sha256(content);
        String fingerprint = IndexConfig.computeFingerprint(actualHash, indexConfig());

        var existing = new KnowledgeRepository.KnowledgeDocumentRecord(
            "existing-id", "title.md", "path", 3, "ACTIVE",
            actualHash, fingerprint, java.time.Instant.now()
        );
        when(knowledgeRepository.findByContentHash(actualHash)).thenReturn(Optional.of(existing));

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", content, false, indexConfig());

        assertEquals(IngestStatus.SKIPPED, result.status());
        assertEquals("existing-id", result.docId());
    }

    @Test
    void ingest_sameContentDifferentConfig_returnsUpdated() {
        String content = "content for updated config test";
        String actualHash = sha256(content);
        // Existing doc was indexed with a different config (different fingerprint)
        String oldFingerprint = IndexConfig.computeFingerprint(actualHash,
            new IndexConfig("old-model", "0.9", 500, 50, "0.5"));

        var existing = new KnowledgeRepository.KnowledgeDocumentRecord(
            "existing-id", "title.md", "path", 3, "ACTIVE",
            actualHash, oldFingerprint, java.time.Instant.now()
        );
        when(knowledgeRepository.findByContentHash(actualHash)).thenReturn(Optional.of(existing));
        when(textSplitter.split(any(), any())).thenReturn(List.of(
            new MarkdownTextSplitter.DocumentChunk("c1", "tmp", 0, "# H", "content", 10)
        ));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f}));

        DocumentIngestService.IngestResult result =
            ingestService.ingest("title.md", "path", content, false, indexConfig());

        assertEquals(IngestStatus.UPDATED, result.status());
        assertEquals("existing-id", result.docId());
        verify(knowledgeRepository).deleteChunksByDocId("existing-id");
        verify(knowledgeRepository).updateDocumentMetadata(eq("existing-id"), any(), any(), anyInt(), any(), any());
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
        when(knowledgeRepository.findByContentHash(any())).thenReturn(Optional.of(existing));
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
