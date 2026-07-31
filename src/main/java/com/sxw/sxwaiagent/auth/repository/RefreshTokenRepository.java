package com.sxw.sxwaiagent.auth.repository;

import com.sxw.sxwaiagent.auth.model.RefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class RefreshTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRepository.class);

    private static final RowMapper<RefreshToken> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp revokedTs = rs.getTimestamp("revoked_at");
        return new RefreshToken(
                rs.getLong("id"),
                rs.getString("token_hash"),
                rs.getString("username"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                revokedTs != null ? revokedTs.toInstant() : null
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存 RefreshToken（INSERT，token_hash 由调用方计算 SHA-256 后传入）。
     */
    public RefreshToken save(RefreshToken token) {
        jdbcTemplate.update("""
            INSERT INTO ai_refresh_token (token_hash, username, expires_at, created_at)
            VALUES (?, ?, ?, ?)
            """,
                token.tokenHash(),
                token.username(),
                Timestamp.from(token.expiresAt()),
                Timestamp.from(token.createdAt()));
        return token;
    }

    /**
     * 根据 token hash 查询（未 revoked 且未过期的优先）。
     */
    public Optional<RefreshToken> findByTokenHash(String hash) {
        var results = jdbcTemplate.query("""
            SELECT id, token_hash, username, expires_at, created_at, revoked_at
            FROM ai_refresh_token
            WHERE token_hash = ?
            """, ROW_MAPPER, hash);
        return results.stream().findFirst();
    }

    /**
     * 根据 token hash 标记为 revoked。
     */
    public int revokeByTokenHash(String hash) {
        return jdbcTemplate.update("""
            UPDATE ai_refresh_token SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL
            """, Timestamp.from(Instant.now()), hash);
    }

    /**
     * 批量 revoke 某用户所有尚未 revoked 的 token。
     */
    public int revokeByUsername(String username) {
        return jdbcTemplate.update("""
            UPDATE ai_refresh_token SET revoked_at = ? WHERE username = ? AND revoked_at IS NULL
            """, Timestamp.from(Instant.now()), username);
    }

    /**
     * 清理已过期的记录。
     */
    public int deleteExpired() {
        int deleted = jdbcTemplate.update("""
            DELETE FROM ai_refresh_token WHERE expires_at < ?
            """, Timestamp.from(Instant.now()));
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
        return deleted;
    }
}
