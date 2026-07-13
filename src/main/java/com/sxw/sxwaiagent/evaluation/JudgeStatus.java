package com.sxw.sxwaiagent.evaluation;

/**
 * Outcome of the LLM-as-Judge evaluation phase.
 */
public enum JudgeStatus {
    PASSED,
    FAILED,
    UNAVAILABLE,
    TIMEOUT,
    PARSE_ERROR
}
