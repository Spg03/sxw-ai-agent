package com.sxw.sxwaiagent.evaluation.comparison;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.evaluation.JudgeStatus;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class EvalComparisonRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EvalComparisonRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void create(EvalComparison comparison) {
        jdbc.update("""
            INSERT INTO ai_eval_comparison
            (comparison_id,name,case_ids_json,targets_json,repeats,status,triggered_by,created_at,updated_at)
            VALUES (?,?,?,?,?,'QUEUED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, comparison.comparisonId(), comparison.name(), json(comparison.caseIds()),
            json(comparison.targets().stream().map(Enum::name).toList()), comparison.repeats(), comparison.triggeredBy());
    }

    public Optional<EvalComparison> find(String comparisonId) {
        List<EvalComparison> values = jdbc.query(
            "SELECT * FROM ai_eval_comparison WHERE comparison_id=?", this::mapComparison, comparisonId);
        return values.stream().findFirst();
    }

    @Transactional
    public Optional<EvalComparison> claimNext() {
        List<EvalComparison> values = jdbc.query("""
            WITH picked AS (
                SELECT id FROM ai_eval_comparison
                WHERE status='QUEUED' AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)
                ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE ai_eval_comparison c SET status='RUNNING', started_at=CURRENT_TIMESTAMP,
                attempt_count=attempt_count+1, updated_at=CURRENT_TIMESTAMP
            FROM picked WHERE c.id=picked.id RETURNING c.*
            """, this::mapComparison);
        return values.stream().findFirst();
    }

    public void complete(String comparisonId) {
        jdbc.update("""
            UPDATE ai_eval_comparison SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,
            updated_at=CURRENT_TIMESTAMP,error_message=NULL WHERE comparison_id=?
            """, comparisonId);
    }

    public void retryOrFail(String comparisonId, int attemptCount, String error) {
        if (attemptCount >= 3) {
            jdbc.update("""
                UPDATE ai_eval_comparison SET status='FAILED',completed_at=CURRENT_TIMESTAMP,
                error_message=?,updated_at=CURRENT_TIMESTAMP WHERE comparison_id=?
                """, bounded(error), comparisonId);
        } else {
            jdbc.update("""
                UPDATE ai_eval_comparison SET status='QUEUED',next_retry_at=CURRENT_TIMESTAMP + INTERVAL '30 seconds',
                error_message=?,updated_at=CURRENT_TIMESTAMP WHERE comparison_id=?
                """, bounded(error), comparisonId);
        }
    }

    public void saveResult(EvalTargetResult result) {
        jdbc.update("""
            INSERT INTO ai_eval_target_result
            (comparison_id,case_id,case_name,target_code,target_version,model,config_hash,repeat_index,
             passed,keyword_passed,judge_status,judge_score,judge_reason,actual_output,expected_output,
             stop_reason,input_tokens,output_tokens,latency_ms,tool_call_count,tool_success_rate,
             security_violation_count,event_log_object_key,error_category,error_message,metrics_json)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (comparison_id,case_id,target_code,repeat_index) DO NOTHING
            """, result.comparisonId(), result.caseId(), result.caseName(), result.targetCode().name(),
            result.targetVersion(), result.model(), result.configHash(), result.repeatIndex(), result.passed(),
            result.keywordPassed(), result.judgeStatus() == null ? null : result.judgeStatus().name(),
            result.judgeScore(), result.judgeReason(), result.actualOutput(), result.expectedOutput(),
            result.stopReason(), result.inputTokens(), result.outputTokens(), result.latencyMs(),
            result.toolCallCount(), result.toolSuccessRate(), result.securityViolationCount(),
            result.eventLogObjectKey(), result.errorCategory(), result.errorMessage(), result.metricsJson());
    }

    public List<EvalTargetResult> results(String comparisonId) {
        return jdbc.query("""
            SELECT * FROM ai_eval_target_result WHERE comparison_id=?
            ORDER BY case_id,target_code,repeat_index
            """, this::mapResult, comparisonId);
    }

    private EvalComparison mapComparison(ResultSet rs, int row) throws SQLException {
        return new EvalComparison(rs.getString("comparison_id"), rs.getString("name"),
            stringList(rs.getString("case_ids_json")),
            stringList(rs.getString("targets_json")).stream().map(HarnessTargetCode::valueOf).toList(),
            rs.getInt("repeats"), EvalComparisonStatus.valueOf(rs.getString("status")),
            rs.getInt("attempt_count"), rs.getString("triggered_by"),
            timestamp(rs, "started_at"), timestamp(rs, "completed_at"), rs.getString("error_message"),
            timestamp(rs, "created_at"));
    }

    private EvalTargetResult mapResult(ResultSet rs, int row) throws SQLException {
        String judge = rs.getString("judge_status");
        return new EvalTargetResult(rs.getString("comparison_id"), rs.getString("case_id"),
            rs.getString("case_name"), HarnessTargetCode.valueOf(rs.getString("target_code")),
            rs.getString("target_version"), rs.getString("model"), rs.getString("config_hash"),
            rs.getInt("repeat_index"), rs.getBoolean("passed"), nullableBoolean(rs, "keyword_passed"),
            judge == null ? null : JudgeStatus.valueOf(judge), nullableDouble(rs, "judge_score"),
            rs.getString("judge_reason"), rs.getString("actual_output"), rs.getString("expected_output"),
            rs.getString("stop_reason"), nullableInt(rs, "input_tokens"), nullableInt(rs, "output_tokens"),
            rs.getLong("latency_ms"), rs.getInt("tool_call_count"), rs.getDouble("tool_success_rate"),
            rs.getInt("security_violation_count"), rs.getString("event_log_object_key"),
            rs.getString("error_category"), rs.getString("error_message"), rs.getString("metrics_json"));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid comparison JSON", e); }
    }
    private List<String> stringList(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception e) { throw new IllegalStateException("Invalid comparison JSON", e); }
    }
    private static java.time.LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column); return rs.wasNull() ? null : value;
    }
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column); return rs.wasNull() ? null : value;
    }
    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column); return rs.wasNull() ? null : value;
    }
    private static String bounded(String value) {
        if (value == null) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
