package com.sxw.sxwaiagent.agent.profile;

/**
 * 输出策略
 *
 * @param structured       是否结构化输出
 * @param includeCitations 是否包含引用
 * @param maxTokens        最大输出 token 数
 */
public record OutputPolicy(
        boolean structured,
        boolean includeCitations,
        int maxTokens
) {
    
    /**
     * 默认策略（非结构化，包含引用，2048 tokens）
     */
    public static OutputPolicy defaults() {
        return new OutputPolicy(false, true, 2048);
    }
    
    /**
     * 结构化输出策略
     */
    public static OutputPolicy structured(int maxTokens) {
        return new OutputPolicy(true, true, maxTokens);
    }
    
    /**
     * 简洁策略（无引用，限制 token）
     */
    public static OutputPolicy concise(int maxTokens) {
        return new OutputPolicy(false, false, maxTokens);
    }
}
