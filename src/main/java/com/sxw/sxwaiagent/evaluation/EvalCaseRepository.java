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
 * 评测用例仓储
 */
@Repository
public class EvalCaseRepository {

    private static final Logger log = LoggerFactory.getLogger(EvalCaseRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public EvalCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EvalCase evalCase) {
        String sql = """
            INSERT INTO ai_eval_case (
                case_id, case_name, case_type, status, profile_code,
                input_prompt, expected_output, validation_rules, tags,
                priority, created_by, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (case_id) DO UPDATE SET
                case_name = EXCLUDED.case_name,
                case_type = EXCLUDED.case_type,
                status = EXCLUDED.status,
                input_prompt = EXCLUDED.input_prompt,
                expected_output = EXCLUDED.expected_output,
                validation_rules = EXCLUDED.validation_rules,
                tags = EXCLUDED.tags,
                priority = EXCLUDED.priority,
                updated_at = EXCLUDED.updated_at
            """;

        String tagsStr = evalCase.tags() != null ? String.join(",", evalCase.tags()) : "";

        jdbcTemplate.update(sql,
            evalCase.caseId(),
            evalCase.caseName(),
            evalCase.caseType().name(),
            evalCase.status().name(),
            evalCase.profileCode(),
            evalCase.inputPrompt(),
            evalCase.expectedOutput(),
            evalCase.validationRules(),
            tagsStr,
            evalCase.priority(),
            evalCase.createdBy(),
            evalCase.createdAt(),
            evalCase.updatedAt()
        );

        log.debug("Saved eval case: {}", evalCase.caseId());
    }

    public Optional<EvalCase> findByCaseId(String caseId) {
        String sql = "SELECT * FROM ai_eval_case WHERE case_id = ?";
        List<EvalCase> cases = jdbcTemplate.query(sql, new EvalCaseRowMapper(), caseId);
        return cases.isEmpty() ? Optional.empty() : Optional.of(cases.get(0));
    }

    public List<EvalCase> findByStatus(EvalCaseStatus status) {
        String sql = "SELECT * FROM ai_eval_case WHERE status = ? ORDER BY priority DESC, created_at DESC";
        return jdbcTemplate.query(sql, new EvalCaseRowMapper(), status.name());
    }

    public List<EvalCase> findByProfileCode(String profileCode) {
        String sql = "SELECT * FROM ai_eval_case WHERE profile_code = ? AND status = 'ACTIVE' ORDER BY priority DESC";
        return jdbcTemplate.query(sql, new EvalCaseRowMapper(), profileCode);
    }

    public List<EvalCase> findByType(EvalCaseType caseType) {
        String sql = "SELECT * FROM ai_eval_case WHERE case_type = ? AND status = 'ACTIVE' ORDER BY priority DESC";
        return jdbcTemplate.query(sql, new EvalCaseRowMapper(), caseType.name());
    }

    public List<EvalCase> findAllActive() {
        String sql = "SELECT * FROM ai_eval_case WHERE status = 'ACTIVE' ORDER BY priority DESC, created_at DESC";
        return jdbcTemplate.query(sql, new EvalCaseRowMapper());
    }

    public List<EvalCase> findByIds(List<String> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = caseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM ai_eval_case WHERE case_id IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, new EvalCaseRowMapper(), caseIds.toArray());
    }

    public void updateStatus(String caseId, EvalCaseStatus status) {
        String sql = "UPDATE ai_eval_case SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE case_id = ?";
        jdbcTemplate.update(sql, status.name(), caseId);
    }

    public long countActive() {
        String sql = "SELECT COUNT(*) FROM ai_eval_case WHERE status = 'ACTIVE'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    private static class EvalCaseRowMapper implements RowMapper<EvalCase> {
        @Override
        public EvalCase mapRow(ResultSet rs, int rowNum) throws SQLException {
            String tagsStr = rs.getString("tags");
            List<String> tags = (tagsStr != null && !tagsStr.isEmpty())
                ? Arrays.asList(tagsStr.split(","))
                : Collections.emptyList();

            return new EvalCase(
                rs.getLong("id"),
                rs.getString("case_id"),
                rs.getString("case_name"),
                EvalCaseType.valueOf(rs.getString("case_type")),
                EvalCaseStatus.valueOf(rs.getString("status")),
                rs.getString("profile_code"),
                rs.getString("input_prompt"),
                rs.getString("expected_output"),
                rs.getString("validation_rules"),
                tags,
                rs.getInt("priority"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}
