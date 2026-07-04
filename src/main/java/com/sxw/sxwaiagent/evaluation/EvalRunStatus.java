package com.sxw.sxwaiagent.evaluation;

/**
 * 评测运行状态
 */
public enum EvalRunStatus {
    /**
     * 等待执行
     */
    PENDING,
    
    /**
     * 执行中
     */
    RUNNING,
    
    /**
     * 已完成
     */
    COMPLETED,
    
    /**
     * 已失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED
}
