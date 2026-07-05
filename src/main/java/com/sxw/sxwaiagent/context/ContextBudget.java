package com.sxw.sxwaiagent.context;

/**
 * Context Token budget configuration.
 * <p>
 * Defines token budget allocation ratios and values for each region.
 * Default uses qwen-plus 8K as example: maxInput=8192, reservedOutput=2048, available=6144
 */
public record ContextBudget(
        int maxInputTokens,       // Model max input tokens
        int reservedOutputTokens, // Reserved output tokens
        int availableTokens,      // Available tokens = maxInput - reservedOutput
        
        // Region budget allocations
        int staticBudget,         // Static region: 30%
        int memoryBudget,         // Memory region: 15%
        int knowledgeBudget,      // Knowledge region: 20%
        int toolResultBudget,     // Tool results: 20%
        int historyBudget         // Conversation history: 15%
) {
    
    /**
     * Default budget (qwen-plus 8K).
     */
    public static ContextBudget defaultBudget() {
        return of(8192, 2048);
    }
    
    /**
     * Custom budget.
     */
    public static ContextBudget of(int maxInputTokens, int reservedOutputTokens) {
        int available = maxInputTokens - reservedOutputTokens;
        
        return new ContextBudget(
                maxInputTokens,
                reservedOutputTokens,
                available,
                (int) (available * 0.30),  // Static 30%
                (int) (available * 0.15),  // Memory 15%
                (int) (available * 0.20),  // Knowledge 20%
                (int) (available * 0.20),  // Tool 20%
                (int) (available * 0.15)   // History 15%
        );
    }
    
    /**
     * Check if over budget.
     */
    public boolean isOverBudget(int currentTokens) {
        return currentTokens > availableTokens;
    }
    
    /**
     * Get remaining available tokens.
     */
    public int remainingTokens(int usedTokens) {
        return Math.max(0, availableTokens - usedTokens);
    }
}
