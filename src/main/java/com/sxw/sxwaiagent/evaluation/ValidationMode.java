package com.sxw.sxwaiagent.evaluation;

/**
 * Determines how keyword and LLM-judge validation results are combined.
 */
public enum ValidationMode {
    KEYWORD_ONLY,
    LLM_ONLY,
    ALL,
    ANY
}
