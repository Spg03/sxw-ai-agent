package com.sxw.sxwaiagent.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Eval run.
 * <p>
 * Records complete information for one eval execution, including run cases, results, and statistics.
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
     * Convenience constructor: create run in PENDING status.
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
     * Check if run can start.
     */
    public boolean canStart() {
        return status == EvalRunStatus.PENDING;
    }

    /**
     * Check if run is completed.
     */
    public boolean isCompleted() {
        return status == EvalRunStatus.COMPLETED || status == EvalRunStatus.FAILED;
    }

    /**
     * Calculate pass rate.
     */
    public double calculatePassRate() {
        if (totalCases == null || totalCases == 0) {
            return 0.0;
        }
        int passed = passedCases != null ? passedCases : 0;
        return (double) passed / totalCases * 100.0;
    }

    /**
     * Build summary text.
     */
    public String buildSummary() {
        return String.format("[%s] %s - %s (%.1f%% passed, %d/%d cases)",
            runId, runName, status, calculatePassRate(),
            passedCases != null ? passedCases : 0,
            totalCases != null ? totalCases : 0);
    }
}
