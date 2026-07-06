package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return jdbcTemplate.query("""
            SELECT * FROM ai_hermes_candidate WHERE candidate_id = ?
            """,
            (rs, rowNum) -> {
                if (!rs.next()) return null;
                
                return new HermesCandidate(
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
                        rs.getTimestamp("reviewed_at").toInstant() : null
                );
            },
            candidateId
        );
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
                    rs.getTimestamp("reviewed_at").toInstant() : null
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
}
