package com.sxw.sxwaiagent.memory;

/**
 * 记忆状态
 */
public enum MemoryStatus {
    /**
     * 待审核（Hermes 生成）
     */
    PENDING,
    
    /**
     * 已激活（审核通过）
     */
    ACTIVE,
    
    /**
     * 已过期
     */
    EXPIRED,
    
    /**
     * 已归档
     */
    ARCHIVED
}
