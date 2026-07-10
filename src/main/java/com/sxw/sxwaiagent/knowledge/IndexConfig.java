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
