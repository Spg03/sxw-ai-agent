package com.sxw.sxwaiagent.hermes;

/**
 * Hermes candidate type.
 */
public enum HermesCandidateType {
    /**
     * Memory candidate (user preferences, feedback rules).
     */
    MEMORY,
    
    /**
     * Knowledge candidate (missing docs, FAQ).
     */
    KNOWLEDGE,
    
    /**
     * Eval case (input-output pairs).
     */
    EVAL_CASE,
    
    /**
     * Agent rule (behavior constraints).
     */
    AGENT_RULE,
    
    /**
     * Prompt improvement suggestion.
     */
    PROMPT_IMPROVEMENT,
    
    /**
     * Tool improvement suggestion.
     */
    TOOL_IMPROVEMENT,
    
    /**
     * Documentation update suggestion.
     */
    DOC_UPDATE
}
