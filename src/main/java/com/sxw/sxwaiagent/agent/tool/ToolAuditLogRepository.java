package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
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

/**
 * 工具审计日志持久化 Repository
 * <p>
 * 基于 JdbcTemplate 操作 ai_tool_audit_log 表，
 * 替代内存 ConcurrentHashMap，支持容量限制（保留最近 MAX_RECORDS 条）。
 */
@Repository
public class ToolAuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditLogRepository.class);

    /**
     * 容量上限：保留最近 10000 条审计日志，超出后在写入时触发清理
     */
    static final int MAX_RECORDS = 10_000;

    private final JdbcTemplate jdbcTemplate;

    public ToolAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入一条审计日志
     */
    public void insert(ToolAuditLog.AuditEntry entry) {
        jdbcTemplate.update("""
            INSERT INTO ai_tool_audit_log
                (request_id, trace_id, turn, tool_name, risk_level,
                 arguments, result, status, latency_ms, approved, approval_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            entry.requestId(),
            entry.traceId(),
            entry.turn(),
            entry.toolName(),
            entry.riskLevel() != null ? entry.riskLevel().name() : null,
            entry.arguments(),
            entry.result(),
            entry.status(),
            entry.latencyMs(),
            entry.approved(),
            entry.approvalId(),
            Timestamp.from(Instant.ofEpochMilli(entry.timestamp()))
        );

        log.debug("Inserted audit log: requestId={}, tool={}, status={}",
                entry.requestId(), entry.toolName(), entry.status());
    }

    /**
     * 按 requestId 查询审计日志
     */
    public List<ToolAuditLog.AuditEntry> findByRequestId(String requestId) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, turn, tool_name, risk_level,
                   arguments, result, status, latency_ms, approved, approval_id, created_at
            FROM ai_tool_audit_log
            WHERE request_id = ?
            ORDER BY created_at ASC
            """, ROW_MAPPER, requestId);
    }

    /**
     * 按 traceId 查询审计日志
     */
    public List<ToolAuditLog.AuditEntry> findByTraceId(String traceId) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, turn, tool_name, risk_level,
                   arguments, result, status, latency_ms, approved, approval_id, created_at
            FROM ai_tool_audit_log
            WHERE trace_id = ?
            ORDER BY created_at ASC
            """, ROW_MAPPER, traceId);
    }

    /**
     * 按 toolName 查询审计日志（最多返回 1000 条，避免 OOM）
     */
    public List<ToolAuditLog.AuditEntry> findByToolName(String toolName) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, turn, tool_name, risk_level,
                   arguments, result, status, latency_ms, approved, approval_id, created_at
            FROM ai_tool_audit_log
            WHERE tool_name = ?
            ORDER BY created_at DESC
            LIMIT 1000
            """, ROW_MAPPER, toolName);
    }

    /**
     * 删除指定 requestId 的审计日志
     */
    public void deleteByRequestId(String requestId) {
        int deleted = jdbcTemplate.update("DELETE FROM ai_tool_audit_log WHERE request_id = ?", requestId);
        if (deleted > 0) {
            log.debug("Deleted {} audit logs for requestId={}", deleted, requestId);
        }
    }

    /**
     * 获取总记录数
     */
    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)::int FROM ai_tool_audit_log", Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 容量清理：保留最新的 MAX_RECORDS 条，删除更早的记录。
     * 在每次写入后按需调用，防止表无限增长。
     */
    public void trimToCapacity() {
        int current = count();
        if (current <= MAX_RECORDS) {
            return;
        }
        int toDelete = current - MAX_RECORDS;
        int deleted = jdbcTemplate.update("""
            DELETE FROM ai_tool_audit_log
            WHERE id IN (
                SELECT id FROM ai_tool_audit_log
                ORDER BY created_at ASC
                LIMIT ?
            )
            """, toDelete);
        log.info("Trimmed {} old audit log records to maintain capacity of {}", deleted, MAX_RECORDS);
    }

    // ────────────────────────────────────────────────
    // RowMapper：将数据库行映射为 AuditEntry record
    // ────────────────────────────────────────────────
    private static final RowMapper<ToolAuditLog.AuditEntry> ROW_MAPPER = new RowMapper<>() {
        @Override
        public ToolAuditLog.AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            String riskLevelStr = rs.getString("risk_level");
            ToolRiskLevel riskLevel = null;
            if (riskLevelStr != null) {
                try {
                    riskLevel = ToolRiskLevel.valueOf(riskLevelStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown risk level in DB: {}", riskLevelStr);
                }
            }

            Timestamp ts = rs.getTimestamp("created_at");
            long timestamp = ts != null ? ts.getTime() : 0L;

            return new ToolAuditLog.AuditEntry(
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getInt("turn"),
                rs.getString("tool_name"),
                riskLevel,
                rs.getString("arguments"),
                rs.getString("result"),
                rs.getString("status"),
                rs.getLong("latency_ms"),
                rs.getBoolean("approved"),
                rs.getString("approval_id"),
                timestamp
            );
        }
    };
}
