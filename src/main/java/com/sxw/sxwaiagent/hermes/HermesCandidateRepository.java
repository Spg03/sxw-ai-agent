package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HermesCandidateRepository {
    
    private static final Logger log = LoggerFactory.getLogger(HermesCandidateRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public HermesCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public String save(HermesCandidate candidate) {
        String candidateId = candidate.candidateId() != null ? 
            candidate.candidateId() : UUID.randomUUID().toString();
        
        jdbcTemplate.update("""
            INSERT INTO ai_hermes_candidate (
                candidate_id, run_id, chat_id, type, title, content, metadata,
                status, reviewed_by, created_at, reviewed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            candidateId,
            candidate.runId(),
            candidate.chatId(),
            candidate.type().name(),
            candidate.title(),
            candidate.content(),
            candidate.metadata(),
            candidate.status().name(),
            candidate.reviewedBy(),
            Timestamp.from(candidate.createdAt()),
            candidate.reviewedAt() != null ? Timestamp.from(candidate.reviewedAt()) : null
        );
        
        log.info("Saved Hermes candidate: {} ({})", candidateId, candidate.type());
        return candidateId;
    }
    
    public Optional<HermesCandidate> findById(String candidateId) {
        return findById(candidateId, false);
    }

    public Optional<HermesCandidate> findByIdForUpdate(String candidateId) {
        return findById(candidateId, true);
    }

    private Optional<HermesCandidate> findById(String candidateId, boolean lock) {
        List<HermesCandidate> results;
        try {
            results = jdbcTemplate.query("""
                SELECT * FROM ai_hermes_candidate WHERE candidate_id = ?
                """ + (lock ? " FOR UPDATE NOWAIT" : ""),
                (rs, rowNum) -> new HermesCandidate(
                    rs.getString("candidate_id"),
                    rs.getString("run_id"),
                    rs.getString("chat_id"),
                    CandidateType.valueOf(rs.getString("type")),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getString("metadata"),
                    HermesCandidate.CandidateStatus.valueOf(rs.getString("status")),
                    rs.getString("reviewed_by"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("reviewed_at") != null ?
                        rs.getTimestamp("reviewed_at").toInstant() : null,
                    rs.getString("run_id"),  // sourceTraceId derived from runId
                    null                     // confidence (DB column not yet added)
                ),
                candidateId
            );
        } catch (UncategorizedSQLException e) {
            // PostgreSQL 55P03 is lock_not_available. Some Spring JDBC versions do not
            // translate it, so normalize it before Hermes handles a busy candidate.
            if (lock && "55P03".equals(e.getSQLException().getSQLState())) {
                throw new CannotAcquireLockException("Hermes candidate is being applied", e);
            }
            throw e;
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
    
    public List<HermesCandidate> findByStatus(HermesCandidate.CandidateStatus status) {
        return jdbcTemplate.query("""
            SELECT * FROM ai_hermes_candidate 
            WHERE status = ?
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new HermesCandidate(
                rs.getString("candidate_id"),
                rs.getString("run_id"),
                rs.getString("chat_id"),
                CandidateType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("metadata"),
                HermesCandidate.CandidateStatus.valueOf(rs.getString("status")),
                rs.getString("reviewed_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("reviewed_at") != null ? 
                    rs.getTimestamp("reviewed_at").toInstant() : null,
                rs.getString("run_id"),  // sourceTraceId derived from runId
                null                     // confidence (DB column not yet added)
            ),
            status.name()
        );
    }
    
    public void updateStatus(String candidateId, HermesCandidate.CandidateStatus status, String reviewedBy) {
        jdbcTemplate.update("""
            UPDATE ai_hermes_candidate 
            SET status = ?, reviewed_by = ?, reviewed_at = ?
            WHERE candidate_id = ?
            """,
            status.name(),
            reviewedBy,
            Timestamp.from(Instant.now()),
            candidateId
        );
        
        log.info("Updated candidate {} status to {} by {}", candidateId, status, reviewedBy);
    }

    public boolean transition(String candidateId, HermesCandidate.CandidateStatus expected,
                              HermesCandidate.CandidateStatus next, String reviewedBy) {
        return jdbcTemplate.update("""
                UPDATE ai_hermes_candidate SET status=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP
                WHERE candidate_id=? AND status=?
                """, next.name(), reviewedBy, candidateId, expected.name()) == 1;
    }

    /** Includes approvals interrupted before their application transaction completed. */
    public List<HermesCandidate> findRecoverable() {
        List<String> ids = jdbcTemplate.queryForList("""
                SELECT candidate_id FROM ai_hermes_candidate
                WHERE status='APPLY_FAILED' OR (status='APPROVED' AND reviewed_at < ?)
                ORDER BY created_at DESC
                """, String.class, Timestamp.from(Instant.now().minusSeconds(600)));
        return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
    }
}
