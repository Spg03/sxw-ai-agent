package com.sxw.sxwaiagent.eval;

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
public class EvalRepository {
    
    private static final Logger log = LoggerFactory.getLogger(EvalRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public EvalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public String saveCase(EvalCase evalCase) {
        String caseId = evalCase.caseId() != null ? evalCase.caseId() : UUID.randomUUID().toString();
        
        jdbcTemplate.update("""
            INSERT INTO ai_eval_case (case_id, name, input, expected_output, tags, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            caseId,
            evalCase.name(),
            evalCase.input(),
            evalCase.expectedOutput(),
            evalCase.tags(),
            Timestamp.from(evalCase.createdAt())
        );
        
        log.info("Saved eval case: {}", caseId);
        return caseId;
    }
    
    public void saveResult(EvalResult result) {
        jdbcTemplate.update("""
            INSERT INTO ai_eval_result (result_id, case_id, agent_type, actual_output, passed, latency_ms, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            result.resultId(),
            result.caseId(),
            result.agentType(),
            result.actualOutput(),
            result.passed(),
            result.latencyMs(),
            result.details(),
            Timestamp.from(result.createdAt())
        );
        
        log.debug("Saved eval result: {}", result.resultId());
    }
    
    public Optional<EvalCase> findCaseById(String caseId) {
        List<EvalCase> results = jdbcTemplate.query("""
            SELECT * FROM ai_eval_case WHERE case_id = ?
            """,
            (rs, rowNum) -> new EvalCase(
                rs.getString("case_id"),
                rs.getString("name"),
                rs.getString("input"),
                rs.getString("expected_output"),
                rs.getString("tags"),
                rs.getTimestamp("created_at").toInstant()
            ),
            caseId
        );
        
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<EvalCase> findAllCases() {
        return jdbcTemplate.query("""
            SELECT * FROM ai_eval_case ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new EvalCase(
                rs.getString("case_id"),
                rs.getString("name"),
                rs.getString("input"),
                rs.getString("expected_output"),
                rs.getString("tags"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
    }
    
    public List<EvalResult> findResultsByCaseId(String caseId) {
        return jdbcTemplate.query("""
            SELECT * FROM ai_eval_result 
            WHERE case_id = ?
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new EvalResult(
                rs.getString("result_id"),
                rs.getString("case_id"),
                rs.getString("agent_type"),
                rs.getString("actual_output"),
                rs.getBoolean("passed"),
                rs.getLong("latency_ms"),
                rs.getString("details"),
                rs.getTimestamp("created_at").toInstant()
            ),
            caseId
        );
    }
}
