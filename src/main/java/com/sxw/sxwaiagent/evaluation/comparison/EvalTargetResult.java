package com.sxw.sxwaiagent.evaluation.comparison;

import com.sxw.sxwaiagent.evaluation.JudgeStatus;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;

public record EvalTargetResult(
    String comparisonId,
    String caseId,
    String caseName,
    HarnessTargetCode targetCode,
    String targetVersion,
    String model,
    String configHash,
    int repeatIndex,
    boolean passed,
    Boolean keywordPassed,
    JudgeStatus judgeStatus,
    Double judgeScore,
    String judgeReason,
    String actualOutput,
    String expectedOutput,
    String stopReason,
    Integer inputTokens,
    Integer outputTokens,
    long latencyMs,
    int toolCallCount,
    double toolSuccessRate,
    int securityViolationCount,
    String eventLogObjectKey,
    String errorCategory,
    String errorMessage,
    String metricsJson
) { }
