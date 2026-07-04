package com.sxw.sxwaiagent.evaluation;

/**
 * 评测结果
 */
public record EvalResult(
    String caseId,
    String caseName,
    boolean passed,
    String actualOutput,
    String expectedOutput,
    String validationDetails,
    long durationMs,
    String errorMessage
) {
    /**
     * 创建通过结果
     */
    public static EvalResult pass(String caseId, String caseName, String actualOutput, String expectedOutput, long durationMs) {
        return new EvalResult(caseId, caseName, true, actualOutput, expectedOutput, null, durationMs, null);
    }

    /**
     * 创建失败结果
     */
    public static EvalResult fail(String caseId, String caseName, String actualOutput, String expectedOutput, String validationDetails, long durationMs) {
        return new EvalResult(caseId, caseName, false, actualOutput, expectedOutput, validationDetails, durationMs, null);
    }

    /**
     * 创建错误结果
     */
    public static EvalResult error(String caseId, String caseName, String errorMessage, long durationMs) {
        return new EvalResult(caseId, caseName, false, null, null, null, durationMs, errorMessage);
    }
}
