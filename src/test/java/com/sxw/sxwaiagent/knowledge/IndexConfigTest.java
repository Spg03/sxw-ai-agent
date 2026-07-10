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
