package com.sxw.sxwaiagent.plan;

import java.time.Instant;
import java.util.List;

/**
 * 执行计划
 * 
 * 记录 Agent 的任务分解结果，包含多个步骤。
 * 用户审核通过后，Agent 进入 EXECUTE 模式按计划执行。
 */
public record Plan(
    String planId,
    String chatId,
    String goal,
    List<PlanStep> steps,
    PlanStatus status,
    String createdBy,
    String reviewedBy,
    Instant createdAt,
    Instant reviewedAt
) {
    
    public record PlanStep(
        int stepIndex,
        String description,
        String toolName,
        StepStatus status
    ) {}
    
    public enum PlanStatus {
        DRAFT,      // 草稿
        PENDING,    // 待审核
        APPROVED,   // 已批准
        REJECTED,   // 已拒绝
        COMPLETED   // 已完成
    }
    
    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
