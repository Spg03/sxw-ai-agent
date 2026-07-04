package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Hermes 候选仓储
 * <p>
 * 负责 HermesCandidate 的持久化操作。
 */
@Repository
public class HermesCandidateRepository {
    
    private static final Logger log = LoggerFactory.getLogger(HermesCandidateRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public HermesCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 保存候选
     */
    public void save(HermesCandidate candidate) {
        String sql = """
            INSERT INTO ai_hermes_candidate (
                candidate_id, source_request_id, source_trace_id,
                candidate_type, title, content, target_store,
                confidence, status, created_at,
                reviewed_at, reviewed_by, applied_at, apply_result
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (candidate_id) DO UPDATE SET
                title = EXCLUDED.title,
                content = EXCLUDED.content,
                confidence = EXCLUDED.confidence,
                status = EXCLUDED.status,
                reviewed_at = EXCLUDED.reviewed_at,
                reviewed_by = EXCLUDED.reviewed_by,
                applied_at = EXCLUDED.applied_at,
                apply_result = EXCLUDED.apply_result
            """;
        
        jdbcTemplate.update(sql,
            candidate.candidateId(),
            candidate.sourceRequestId(),
            candidate.sourceTraceId(),
            candidate.candidateType().name(),
            candidate.title(),
            candidate.content(),
            candidate.targetStore(),
            candidate.confidence(),
            candidate.status().name(),
            candidate.createdAt(),
            candidate.reviewedAt(),
            candidate.reviewedBy(),
            candidate.appliedAt(),
            candidate.applyResult()
        );
        
        log.debug("Saved Hermes candidate: {}", candidate.candidateId());
    }
    
    /**
     * 根据 candidateId 查找
     */
    public Optional<HermesCandidate> findByCandidateId(String candidateId) {
        String sql = "SELECT * FROM ai_hermes_candidate WHERE candidate_id = ?";
        List<HermesCandidate> candidates = jdbcTemplate.query(sql, new HermesCandidateRowMapper(), candidateId);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }
    
    /**
     * 根据状态查找候选
     */
    public List<HermesCandidate> findByStatus(HermesCandidateStatus status) {
        String sql = """
            SELECT * FROM ai_hermes_candidate 
            WHERE status = ?
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, new HermesCandidateRowMapper(), status.name());
    }
    
    /**
     * 根据类型和状态查找候选
     */
    public List<HermesCandidate> findByTypeAndStatus(HermesCandidateType type, HermesCandidateStatus status) {
        String sql = """
            SELECT * FROM ai_hermes_candidate 
            WHERE candidate_type = ? AND status = ?
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, new HermesCandidateRowMapper(), type.name(), status.name());
    }
    
    /**
     * 查找所有待审核候选
     */
    public List<HermesCandidate> findAllPending() {
        return findByStatus(HermesCandidateStatus.PENDING);
    }
    
    /**
     * 查找指定 Trace 的所有候选
     */
    public List<HermesCandidate> findByTraceId(String traceId) {
        String sql = """
            SELECT * FROM ai_hermes_candidate 
            WHERE source_trace_id = ?
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, new HermesCandidateRowMapper(), traceId);
    }
    
    /**
     * 更新状态
     */
    public void updateStatus(String candidateId, HermesCandidateStatus status, String reviewedBy) {
        String sql = """
            UPDATE ai_hermes_candidate 
            SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?
            WHERE candidate_id = ?
            """;
        jdbcTemplate.update(sql, status.name(), reviewedBy, candidateId);
        log.debug("Updated candidate status: {} -> {}", candidateId, status);
    }
    
    /**
     * 更新应用结果
     */
    public void updateApplyResult(String candidateId, HermesCandidateStatus status, String applyResult) {
        String sql = """
            UPDATE ai_hermes_candidate 
            SET status = ?, applied_at = CURRENT_TIMESTAMP, apply_result = ?
            WHERE candidate_id = ?
            """;
        jdbcTemplate.update(sql, status.name(), applyResult, candidateId);
        log.debug("Updated candidate apply result: {} -> {}", candidateId, status);
    }
    
    /**
     * 统计待审核数量
     */
    public long countPending() {
        String sql = "SELECT COUNT(*) FROM ai_hermes_candidate WHERE status = 'PENDING'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    private static class HermesCandidateRowMapper implements RowMapper<HermesCandidate> {
        @Override
        public HermesCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new HermesCandidate(
                rs.getLong("id"),
                rs.getString("candidate_id"),
                rs.getString("source_request_id"),
                rs.getString("source_trace_id"),
                HermesCandidateType.valueOf(rs.getString("candidate_type")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("target_store"),
                rs.getBigDecimal("confidence"),
                HermesCandidateStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toLocalDateTime() : null,
                rs.getString("reviewed_by"),
                rs.getTimestamp("applied_at") != null ? rs.getTimestamp("applied_at").toLocalDateTime() : null,
                rs.getString("apply_result")
            );
        }
    }
}
