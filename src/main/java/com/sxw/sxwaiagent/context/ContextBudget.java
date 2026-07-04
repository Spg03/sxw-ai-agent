package com.sxw.sxwaiagent.context;

/**
 * 上下文 Token 预算配置
 * <p>
 * 定义各区域的 Token 预算分配比例和具体数值。
 * 默认以 qwen-plus 8K 为例：maxInput=8192, reservedOutput=2048, available=6144
 */
public record ContextBudget(
        int maxInputTokens,       // 模型最大输入 token
        int reservedOutputTokens, // 预留输出 token
        int availableTokens,      // 可用 token = maxInput - reservedOutput
        
        // 各区域预算分配
        int staticBudget,         // 静态区域：30%
        int memoryBudget,         // 记忆区域：15%
        int knowledgeBudget,      // 知识区域：20%
        int toolResultBudget,     // 工具结果：20%
        int historyBudget         // 对话历史：15%
) {
    
    /**
     * 默认预算（qwen-plus 8K）
     */
    public static ContextBudget defaultBudget() {
        return of(8192, 2048);
    }
    
    /**
     * 自定义预算
     */
    public static ContextBudget of(int maxInputTokens, int reservedOutputTokens) {
        int available = maxInputTokens - reservedOutputTokens;
        
        return new ContextBudget(
                maxInputTokens,
                reservedOutputTokens,
                available,
                (int) (available * 0.30),  // 静态 30%
                (int) (available * 0.15),  // 记忆 15%
                (int) (available * 0.20),  // 知识 20%
                (int) (available * 0.20),  // 工具 20%
                (int) (available * 0.15)   // 历史 15%
        );
    }
    
    /**
     * 检查是否超预算
     */
    public boolean isOverBudget(int currentTokens) {
        return currentTokens > availableTokens;
    }
    
    /**
     * 获取剩余可用 token
     */
    public int remainingTokens(int usedTokens) {
        return Math.max(0, availableTokens - usedTokens);
    }
}
