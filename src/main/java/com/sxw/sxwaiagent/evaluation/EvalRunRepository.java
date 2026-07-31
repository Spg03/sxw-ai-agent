package com.sxw.sxwaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Eval run repository.
 */
@Repository
public class EvalRunRepository {

    private static final Logger log = LoggerFactory.getLogger(EvalRunRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public EvalRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EvalRun run) {
        String sql = """
            INSERT INTO ai_eval_run (
                run_id, run_name, status, profile_code, case_ids,
                total_cases, passed_cases, failed_cases, skipped_cases,
                pass_rate, duration_ms, triggered_by,
                started_at, completed_at, report_path, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (run_id) DO UPDATE SET
                status = EXCLUDED.status,
                total_cases = EXCLUDED.total_cases,
                passed_cases = EXCLUDED.passed_cases,
                failed_cases = EXCLUDED.failed_cases,
                skipped_cases = EXCLUDED.skipped_cases,
                pass_rate = EXCLUDED.pass_rate,
                duration_ms = EXCLUDED.duration_ms,
                completed_at = EXCLUDED.completed_at,
                report_path = EXCLUDED.report_path,
                error_message = EXCLUDED.error_message
            """;

        String caseIdsStr = run.caseIds() != null ? String.join(",", run.caseIds()) : "";

        jdbcTemplate.update(sql,
            run.runId(),
            run.runName(),
            run.status().name(),
            run.profileCode(),
            caseIdsStr,
            run.totalCases(),
            run.passedCases(),
            run.failedCases(),
            run.skippedCases(),
            run.passRate(),
            run.durationMs(),
            run.triggeredBy(),
            run.startedAt(),
            run.completedAt(),
            run.reportPath(),
            run.errorMessage()
        );

        log.debug("Saved eval run: {}", run.runId());
    }

    public Optional<EvalRun> findByRunId(String runId) {
        String sql = "SELECT * FROM ai_eval_run WHERE run_id = ?";
        List<EvalRun> runs = jdbcTemplate.query(sql, new EvalRunRowMapper(), runId);
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM ai_eval_run";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    public List<EvalRun> findByStatus(EvalRunStatus status) {
        String sql = "SELECT * FROM ai_eval_run WHERE status = ? ORDER BY started_at DESC";
        return jdbcTemplate.query(sql, new EvalRunRowMapper(), status.name());
    }

    public List<EvalRun> findByProfileCode(String profileCode) {
        String sql = "SELECT * FROM ai_eval_run WHERE profile_code = ? ORDER BY started_at DESC LIMIT 20";
        return jdbcTemplate.query(sql, new EvalRunRowMapper(), profileCode);
    }

    public List<EvalRun> findRecent(int limit) {
        String sql = "SELECT * FROM ai_eval_run ORDER BY started_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new EvalRunRowMapper(), limit);
    }

    public List<EvalRun> findAll() {
        String sql = "SELECT * FROM ai_eval_run ORDER BY started_at DESC";
        return jdbcTemplate.query(sql, new EvalRunRowMapper());
    }

    public void updateStatus(String runId, EvalRunStatus status) {
        String sql = "UPDATE ai_eval_run SET status = ? WHERE run_id = ?";
        jdbcTemplate.update(sql, status.name(), runId);
    }

    public void updateResults(String runId, EvalRunStatus status, int passed, int failed, int skipped, double passRate, long durationMs) {
        String sql = """
            UPDATE ai_eval_run 
            SET status = ?, passed_cases = ?, failed_cases = ?, skipped_cases = ?,
                pass_rate = ?, duration_ms = ?, completed_at = CURRENT_TIMESTAMP
            WHERE run_id = ?
            """;
        jdbcTemplate.update(sql, status.name(), passed, failed, skipped, passRate, durationMs, runId);
    }

    public void updateError(String runId, String errorMessage) {
        String sql = """
            UPDATE ai_eval_run 
            SET status = 'FAILED', error_message = ?, completed_at = CURRENT_TIMESTAMP
            WHERE run_id = ?
            """;
        jdbcTemplate.update(sql, errorMessage, runId);
    }

    private static class EvalRunRowMapper implements RowMapper<EvalRun> {
        @Override
        public EvalRun mapRow(ResultSet rs, int rowNum) throws SQLException {
            String caseIdsStr = rs.getString("case_ids");
            List<String> caseIds = (caseIdsStr != null && !caseIdsStr.isEmpty())
                ? Arrays.asList(caseIdsStr.split(","))
                : Collections.emptyList();

            return new EvalRun(
                rs.getLong("id"),
                rs.getString("run_id"),
                rs.getString("run_name"),
                EvalRunStatus.valueOf(rs.getString("status")),
                rs.getString("profile_code"),
                caseIds,
                rs.getInt("total_cases"),
                rs.getInt("passed_cases"),
                rs.getInt("failed_cases"),
                rs.getInt("skipped_cases"),
                rs.getDouble("pass_rate"),
                rs.getLong("duration_ms"),
                rs.getString("triggered_by"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toLocalDateTime() : null,
                rs.getString("report_path"),
                rs.getString("error_message")
            );
        }
    }
}
