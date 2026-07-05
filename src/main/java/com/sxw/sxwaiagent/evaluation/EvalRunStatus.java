package com.sxw.sxwaiagent.evaluation;

/**
 * Eval run status.
 */
public enum EvalRunStatus {
    /**
     * Waiting for execution.
     */
    PENDING,
    
    /**
     * Running.
     */
    RUNNING,
    
    /**
     * Completed.
     */
    COMPLETED,
    
    /**
     * Failed.
     */
    FAILED,
    
    /**
     * Cancelled.
     */
    CANCELLED
}
