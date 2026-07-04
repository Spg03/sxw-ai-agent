package com.sxw.sxwaiagent.hermes;

/**
 * Hermes 候选状态
 */
public enum HermesCandidateStatus {
    /**
     * 待审核
     */
    PENDING,
    
    /**
     * 已批准
     */
    APPROVED,
    
    /**
     * 已拒绝
     */
    REJECTED,
    
    /**
     * 已应用
     */
    APPLIED,
    
    /**
     * 应用失败
     */
    FAILED
}
