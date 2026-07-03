package com.sxw.sxwaiagent.agent.profile;

/**
 * 记忆策略
 *
 * @param enabled            是否启用记忆
 * @param maxHistoryMessages 最大历史消息数
 * @param memoryType         记忆类型（短期/长期）
 */
public record MemoryPolicy(
        boolean enabled,
        int maxHistoryMessages,
        MemoryType memoryType
) {
    
    /**
     * 记忆类型枚举
     */
    public enum MemoryType {
        /**
         * 短期记忆（当前会话）
         */
        SHORT_TERM,
        
        /**
         * 长期记忆（跨会话）
         */
        LONG_TERM
    }
    
    /**
     * 默认短期记忆策略
     */
    public static MemoryPolicy shortTerm(int maxMessages) {
        return new MemoryPolicy(true, maxMessages, MemoryType.SHORT_TERM);
    }
    
    /**
     * 默认长期记忆策略
     */
    public static MemoryPolicy longTerm(int maxMessages) {
        return new MemoryPolicy(true, maxMessages, MemoryType.LONG_TERM);
    }
    
    /**
     * 禁用记忆
     */
    public static MemoryPolicy disabled() {
        return new MemoryPolicy(false, 0, MemoryType.SHORT_TERM);
    }
}
