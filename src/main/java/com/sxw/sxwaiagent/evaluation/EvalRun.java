package com.sxw.sxwaiagent.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测运行
 * <p>
 * 记录一次评测执行的完整信息，包括运行的用例、结果和统计数据。
 */
public record EvalRun(
    Long id,
    String runId,
    String runName,
    EvalRunStatus status,
    String profileCode,
    List<String> caseIds,
    Integer totalCases,
    Integer passedCases,
    Integer failedCases,
    Integer skippedCases,
    Double passRate,
    Long durationMs,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String reportPath,
    String errorMessage
) {
    /**
     * 便捷构造函数：创建 PENDING 状态的运行
     */
    public EvalRun(
        String runId,
        String runName,
        String profileCode,
        List<String> caseIds,
        String triggeredBy
    ) {
        this(null, runId, runName, EvalRunStatus.PENDING, profileCode,
            caseIds, caseIds.size(), 0, 0, 0, 0.0, 0L,
            triggeredBy, LocalDateTime.now(), null, null, null);
    }

    /**
     * 检查是否可以开始
     */
    public boolean canStart() {
        return status == EvalRunStatus.PENDING;
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return status == EvalRunStatus.COMPLETED || status == EvalRunStatus.FAILED;
    }

    /**
     * 计算通过率
     */
    public double calculatePassRate() {
        if (totalCases == null || totalCases == 0) {
            return 0.0;
        }
        int passed = passedCases != null ? passedCases : 0;
        return (double) passed / totalCases * 100.0;
    }

    /**
     * 构建摘要文本
     */
    public String buildSummary() {
        return String.format("[%s] %s - %s (%.1f%% passed, %d/%d cases)",
            runId, runName, status, calculatePassRate(),
            passedCases != null ? passedCases : 0,
            totalCases != null ? totalCases : 0);
    }
}
