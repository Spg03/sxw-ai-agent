package com.sxw.sxwaiagent.evaluation;

/**
 * 评测用例状态
 */
public enum EvalCaseStatus {
    /**
     * 草稿
     */
    DRAFT,
    
    /**
     * 待审核
     */
    PENDING,
    
    /**
     * 已激活
     */
    ACTIVE,
    
    /**
     * 已禁用
     */
    DISABLED,
    
    /**
     * 已归档
     */
    ARCHIVED
}
