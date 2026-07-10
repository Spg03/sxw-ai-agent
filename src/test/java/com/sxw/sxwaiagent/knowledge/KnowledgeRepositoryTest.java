package com.sxw.sxwaiagent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Sql("classpath:com/sxw/sxwaiagent/knowledge/init-knowledge-schema.sql")
class KnowledgeRepositoryTest {

    @Autowired
    private KnowledgeRepository repository;

    @Test
    void findByDocId_existing_returnsRecord() {
        // Given: save a document with fingerprint
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
    void deleteChunksByDocId_keepsDocumentIntact() {
        String docId = repository.saveDocument("Doc", "doc.md", 1, "h", "f");

        // Delete chunks (none exist, but verifies method runs without error)
        repository.deleteChunksByDocId(docId);

        // Document still exists
        assertTrue(repository.findByDocId(docId).isPresent());

        // Cleanup
        repository.deleteDocument(docId);
    }

    @Test
    void listDocuments_includesNewFields() {
        String docId = repository.saveDocument("ListTest", "list.md", 4, "listhash", "listfp");

        var docs = repository.listDocuments();
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> found =
            docs.stream().filter(d -> d.docId().equals(docId)).findFirst();

        assertTrue(found.isPresent());
        assertEquals("listhash", found.get().contentHash());
        assertEquals("listfp", found.get().indexFingerprint());

        repository.deleteDocument(docId);
    }

    @Test
    void saveDocument_backwardCompatible_4argOverload() {
        // Old 4-arg overload should still work (fingerprint = null)
        String docId = repository.saveDocument("Compat", "compat.md", 2, "compathash");

        Optional<KnowledgeRepository.KnowledgeDocumentRecord> result = repository.findByDocId(docId);
        assertTrue(result.isPresent());
        assertEquals("compathash", result.get().contentHash());
        assertNull(result.get().indexFingerprint());

        repository.deleteDocument(docId);
    }
}
