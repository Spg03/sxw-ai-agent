package com.sxw.sxwaiagent.plan;

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
public class PlanRepository {
    
    private static final Logger log = LoggerFactory.getLogger(PlanRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public PlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public String savePlan(Plan plan) {
        String planId = plan.planId() != null ? plan.planId() : UUID.randomUUID().toString();
        
        jdbcTemplate.update("""
            INSERT INTO ai_plan (plan_id, chat_id, goal, status, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (plan_id) DO UPDATE SET
                goal = EXCLUDED.goal,
                status = EXCLUDED.status,
                reviewed_by = EXCLUDED.reviewed_by,
                reviewed_at = EXCLUDED.reviewed_at
            """,
            planId,
            plan.chatId(),
            plan.goal(),
            plan.status().name(),
            plan.createdBy(),
            Timestamp.from(plan.createdAt())
        );
        
        // Save steps
        for (Plan.PlanStep step : plan.steps()) {
            jdbcTemplate.update("""
                INSERT INTO ai_plan_step (plan_id, step_index, description, tool_name, status)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (plan_id, step_index) DO UPDATE SET
                    description = EXCLUDED.description,
                    tool_name = EXCLUDED.tool_name,
                    status = EXCLUDED.status
                """,
                planId,
                step.stepIndex(),
                step.description(),
                step.toolName(),
                step.status().name()
            );
        }
        
        log.info("Saved plan: {} with {} steps", planId, plan.steps().size());
        return planId;
    }
    
    public Optional<Plan> findByPlanId(String planId) {
        List<Plan> plans = jdbcTemplate.query("""
            SELECT p.plan_id, p.chat_id, p.goal, p.status, p.created_by, p.reviewed_by,
                   p.created_at, p.reviewed_at,
                   s.step_index, s.description, s.tool_name, s.status as step_status
            FROM ai_plan p
            LEFT JOIN ai_plan_step s ON p.plan_id = s.plan_id
            WHERE p.plan_id = ?
            ORDER BY s.step_index
            """,
            (rs, rowNum) -> null,  // Will be handled by result set extraction
            planId
        );
        
        // Custom extraction logic
        return jdbcTemplate.query("""
            SELECT p.*, s.step_index, s.description, s.tool_name, s.status as step_status
            FROM ai_plan p
            LEFT JOIN ai_plan_step s ON p.plan_id = s.plan_id
            WHERE p.plan_id = ?
            ORDER BY s.step_index
            """,
            rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                
                String id = rs.getString("plan_id");
                String chatId = rs.getString("chat_id");
                String goal = rs.getString("goal");
                Plan.PlanStatus status = Plan.PlanStatus.valueOf(rs.getString("status"));
                String createdBy = rs.getString("created_by");
                String reviewedBy = rs.getString("reviewed_by");
                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                Timestamp reviewedAtTs = rs.getTimestamp("reviewed_at");
                Instant reviewedAt = reviewedAtTs != null ? reviewedAtTs.toInstant() : null;
                
                List<Plan.PlanStep> steps = new java.util.ArrayList<>();
                do {
                    int stepIndex = rs.getInt("step_index");
                    if (!rs.wasNull()) {
                        steps.add(new Plan.PlanStep(
                            stepIndex,
                            rs.getString("description"),
                            rs.getString("tool_name"),
                            Plan.StepStatus.valueOf(rs.getString("step_status"))
                        ));
                    }
                } while (rs.next());
                
                return Optional.of(new Plan(id, chatId, goal, steps, status, createdBy, reviewedBy, createdAt, reviewedAt));
            },
            planId
        );
    }
    
    public List<Plan> findByChatId(String chatId) {
        return jdbcTemplate.query("""
            SELECT DISTINCT plan_id FROM ai_plan WHERE chat_id = ? ORDER BY created_at DESC
            """,
            (rs, rowNum) -> {
                String planId = rs.getString("plan_id");
                return findByPlanId(planId).orElse(null);
            },
            chatId
        ).stream().filter(p -> p != null).toList();
    }
    
    public void updateStatus(String planId, Plan.PlanStatus status, String reviewedBy) {
        jdbcTemplate.update("""
            UPDATE ai_plan 
            SET status = ?, reviewed_by = ?, reviewed_at = ?
            WHERE plan_id = ?
            """,
            status.name(),
            reviewedBy,
            Timestamp.from(Instant.now()),
            planId
        );
        
        log.info("Updated plan {} status to {} by {}", planId, status, reviewedBy);
    }
    
    public void updateStepStatus(String planId, int stepIndex, Plan.StepStatus status) {
        jdbcTemplate.update("""
            UPDATE ai_plan_step 
            SET status = ?
            WHERE plan_id = ? AND step_index = ?
            """,
            status.name(),
            planId,
            stepIndex
        );
    }
}
