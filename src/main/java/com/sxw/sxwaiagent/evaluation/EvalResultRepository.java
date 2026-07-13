package com.sxw.sxwaiagent.evaluation;

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

@Repository
public class EvalResultRepository {

    private static final Logger log = LoggerFactory.getLogger(EvalResultRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public EvalResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveBatch(String runId, List<EvalResult> results) {
        String sql = """
            INSERT INTO ai_eval_result (
                run_id, case_id, case_name, passed, keyword_passed,
                judge_status, judge_model, judge_score, judge_reason,
                score, actual_output, expected_output,
                validation_details, validation_mode,
                duration_ms, error_message, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        for (EvalResult r : results) {
            jdbcTemplate.update(sql,
                runId, r.caseId(), r.caseName(), r.passed(), r.keywordPassed(),
                r.judgeStatus() != null ? r.judgeStatus().name() : null,
                r.judgeModel(), r.judgeScore(), r.judgeReason(),
                r.judgeScore(),  // score = judgeScore
                r.actualOutput(), r.expectedOutput(),
                r.validationDetails(),
                r.validationMode() != null ? r.validationMode().name() : null,
                r.durationMs(), r.errorMessage(),
                Timestamp.from(Instant.now())
            );
        }
        log.debug("Saved {} eval results for run {}", results.size(), runId);
    }

    public List<EvalResult> findByRunId(String runId) {
        String sql = "SELECT * FROM ai_eval_result WHERE run_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, new EvalResultRowMapper(), runId);
    }

    private static class EvalResultRowMapper implements RowMapper<EvalResult> {
        @Override
        public EvalResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            String judgeStatusStr = rs.getString("judge_status");
            JudgeStatus judgeStatus = (judgeStatusStr != null && !judgeStatusStr.isEmpty())
                ? JudgeStatus.valueOf(judgeStatusStr) : null;

            String validationModeStr = rs.getString("validation_mode");
            ValidationMode validationMode = (validationModeStr != null && !validationModeStr.isEmpty())
                ? ValidationMode.valueOf(validationModeStr) : ValidationMode.KEYWORD_ONLY;

            double judgeScoreVal = rs.getDouble("judge_score");
            Double judgeScore = rs.wasNull() ? null : judgeScoreVal;

            return new EvalResult(
                rs.getString("case_id"),
                rs.getString("case_name"),
                rs.getBoolean("passed"),
                rs.getBoolean("keyword_passed"),
                rs.getString("actual_output"),
                rs.getString("expected_output"),
                rs.getString("validation_details"),
                rs.getLong("duration_ms"),
                rs.getString("error_message"),
                judgeStatus,
                rs.getString("judge_model"),
                judgeScore,
                rs.getString("judge_reason"),
                validationMode
            );
        }
    }
}
