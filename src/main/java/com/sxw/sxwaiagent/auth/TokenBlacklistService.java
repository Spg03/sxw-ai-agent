package com.sxw.sxwaiagent.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * JWT Token 黑名单服务。
 * <p>
 * 在用户主动 logout 时将 access token 加入黑名单，
 * JwtAuthenticationFilter 在验证 token 前会检查是否命中黑名单。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final JdbcTemplate jdbcTemplate;

    public TokenBlacklistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 将 token 加入黑名单。
     *
     * @param token       原始 JWT access token
     * @param expiresAtMs token 过期时间（epoch millis），用于定期清理
     */
    public void addToBlacklist(String token, long expiresAtMs) {
        String hash = JwtTokenService.getTokenHash(token);
        Instant expiresAt = Instant.ofEpochMilli(expiresAtMs);
        jdbcTemplate.update("""
            INSERT INTO ai_token_blacklist (token_hash, expires_at, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT (token_hash) DO NOTHING
            """,
                hash,
                Timestamp.from(expiresAt),
                Timestamp.from(Instant.now()));
        log.debug("Token added to blacklist, hash={}", hash);
    }

    /**
     * 检查 token 是否在黑名单中。
     */
    public boolean isBlacklisted(String token) {
        String hash = JwtTokenService.getTokenHash(token);
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM ai_token_blacklist WHERE token_hash = ?
            """, Integer.class, hash);
        return count != null && count > 0;
    }

    /**
     * 定期清理已过期的黑名单记录（每小时执行一次）。
     */
    @Scheduled(fixedDelayString = "${sxw.auth.blacklist-cleanup-interval-ms:3600000}")
    public void cleanupExpired() {
        int deleted = jdbcTemplate.update("""
            DELETE FROM ai_token_blacklist WHERE expires_at < ?
            """, Timestamp.from(Instant.now()));
        if (deleted > 0) {
            log.info("Cleaned up {} expired blacklist entries", deleted);
        }
    }
}
