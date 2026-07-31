package com.sxw.sxwaiagent.auth.model;

import java.time.Instant;

/**
 * JWT Refresh Token 实体，对齐 ai_refresh_token 表。
 */
public record RefreshToken(
        Long id,
        String tokenHash,
        String username,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {

    /**
     * 构建待保存的新 RefreshToken（无 id、无 revokedAt）。
     */
    public static RefreshToken create(String tokenHash, String username, Instant expiresAt) {
        return new RefreshToken(null, tokenHash, username, expiresAt, Instant.now(), null);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }

    public RefreshToken revoke() {
        return new RefreshToken(id, tokenHash, username, expiresAt, createdAt, Instant.now());
    }
}
