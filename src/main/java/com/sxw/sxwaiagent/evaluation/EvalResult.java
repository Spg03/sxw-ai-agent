package com.sxw.sxwaiagent.evaluation;

/**
 * Result of executing a single evaluation case, including keyword and LLM-judge outcomes.
 */
public record EvalResult(
    String caseId,
    String caseName,
    boolean passed,
    boolean keywordPassed,
    String actualOutput,
    String expectedOutput,
    String validationDetails,
    long durationMs,
    String errorMessage,
    JudgeStatus judgeStatus,
    String judgeModel,
    Double judgeScore,
    String judgeReason,
    ValidationMode validationMode
) {
    public static EvalResult pass(String caseId, String caseName, String actualOutput,
                                   String expectedOutput, long durationMs) {
        return new EvalResult(caseId, caseName, true, true,
            actualOutput, expectedOutput, null, durationMs, null,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    public static EvalResult fail(String caseId, String caseName, String actualOutput,
                                   String expectedOutput, String validationDetails, long durationMs) {
        return new EvalResult(caseId, caseName, false, false,
            actualOutput, expectedOutput, validationDetails, durationMs, null,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    public static EvalResult error(String caseId, String caseName, String errorMessage, long durationMs) {
        return new EvalResult(caseId, caseName, false, false,
            null, null, null, durationMs, errorMessage,
            null, null, null, null, ValidationMode.KEYWORD_ONLY);
    }

    /**
     * Full result with both keyword and judge outcomes.
     */
    public static EvalResult withJudge(String caseId, String caseName, boolean passed,
                                        boolean keywordPassed, String actualOutput, String expectedOutput,
                                        String validationDetails, long durationMs,
                                        JudgeStatus judgeStatus, String judgeModel,
                                        Double judgeScore, String judgeReason,
                                        ValidationMode validationMode) {
        return new EvalResult(caseId, caseName, passed, keywordPassed,
            actualOutput, expectedOutput, validationDetails, durationMs, null,
            judgeStatus, judgeModel, judgeScore, judgeReason, validationMode);
    }
}
