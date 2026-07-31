package com.sxw.sxwaiagent.agent.tool;

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
import java.util.Optional;

/**
 * 审批请求持久化 Repository
 * <p>
 * 基于 JdbcTemplate 操作 ai_approval_request 表，
 * 替代内存 ConcurrentHashMap，重启后状态不丢失。
 */
@Repository
public class ApprovalRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(ApprovalRequestRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ApprovalRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入一条审批请求
     */
    public void insert(ApprovalService.ApprovalRequest request) {
        Timestamp expiresAt = request.expiresAt() > 0
                ? Timestamp.from(Instant.ofEpochMilli(request.expiresAt()))
                : null;

        jdbcTemplate.update("""
            INSERT INTO ai_approval_request
                (approval_id, request_id, trace_id, tool_name, arguments,
                 reason, status, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            request.approvalId(),
            request.requestId(),
            request.traceId(),
            request.toolName(),
            request.arguments(),
            request.reason(),
            request.status().name(),
            Timestamp.from(Instant.ofEpochMilli(request.createdAt())),
            expiresAt
        );

        log.debug("Inserted approval request: {}", request.approvalId());
    }

    /**
     * 按 approvalId 查询审批请求
     */
    public Optional<ApprovalService.ApprovalRequest> findByApprovalId(String approvalId) {
        List<ApprovalService.ApprovalRequest> results = jdbcTemplate.query("""
            SELECT approval_id, request_id, trace_id, tool_name, arguments,
                   reason, status, approved_by, comment, created_at, expires_at
            FROM ai_approval_request
            WHERE approval_id = ?
            """, ROW_MAPPER, approvalId);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 更新审批状态（批准/拒绝），同时记录审批人和意见
     */
    public int updateStatus(String approvalId, ApprovalService.ApprovalStatus newStatus,
                            String approvedBy, String comment) {
        return jdbcTemplate.update("""
            UPDATE ai_approval_request
            SET status = ?, approved_by = ?, comment = ?
            WHERE approval_id = ?
            """,
            newStatus.name(),
            approvedBy,
            comment,
            approvalId
        );
    }

    /**
     * 将过期且状态仍为 PENDING 的记录批量更新为 EXPIRED
     */
    public int expirePendingBefore(Instant cutoff) {
        int updated = jdbcTemplate.update("""
            UPDATE ai_approval_request
            SET status = 'EXPIRED'
            WHERE status = 'PENDING' AND expires_at IS NOT NULL AND expires_at < ?
            """, Timestamp.from(cutoff));

        if (updated > 0) {
            log.info("Marked {} approval requests as EXPIRED (cutoff={})", updated, cutoff);
        }
        return updated;
    }

    /**
     * 删除指定 approvalId 的记录（用于测试或手动清理）
     */
    public int deleteByApprovalId(String approvalId) {
        return jdbcTemplate.update("DELETE FROM ai_approval_request WHERE approval_id = ?", approvalId);
    }

    // ────────────────────────────────────────────────
    // RowMapper：将数据库行映射为 ApprovalRequest record
    // ────────────────────────────────────────────────
    private static final RowMapper<ApprovalService.ApprovalRequest> ROW_MAPPER = new RowMapper<>() {
        @Override
        public ApprovalService.ApprovalRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
            String statusStr = rs.getString("status");
            ApprovalService.ApprovalStatus status;
            try {
                status = ApprovalService.ApprovalStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown approval status in DB: {}, treating as PENDING", statusStr);
                status = ApprovalService.ApprovalStatus.PENDING;
            }

            Timestamp createdAt = rs.getTimestamp("created_at");
            long createdMs = createdAt != null ? createdAt.getTime() : 0L;

            Timestamp expiresAt = rs.getTimestamp("expires_at");
            long expiresMs = expiresAt != null ? expiresAt.getTime() : 0L;

            return new ApprovalService.ApprovalRequest(
                rs.getString("approval_id"),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getString("tool_name"),
                rs.getString("arguments"),
                rs.getString("reason"),
                status,
                createdMs,
                expiresMs
            );
        }
    };
}
