package com.sxw.sxwaiagent.evaluation.comparison;

import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import java.time.LocalDateTime;
import java.util.List;

public record EvalComparison(
    String comparisonId,
    String name,
    List<String> caseIds,
    List<HarnessTargetCode> targets,
    int repeats,
    EvalComparisonStatus status,
    int attemptCount,
    String triggeredBy,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String errorMessage,
    LocalDateTime createdAt
) { }
