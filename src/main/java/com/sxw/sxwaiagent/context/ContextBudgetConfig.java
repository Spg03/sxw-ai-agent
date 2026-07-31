package com.sxw.sxwaiagent.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Context budget Spring configuration.
 * <p>
 * Reads max-input-tokens and reserved-output-tokens from application properties,
 * falling back to sensible defaults (qwen-plus 8K: 8192 / 2048).
 */
@Configuration
public class ContextBudgetConfig {

    @Value("${sxw.agent.context.max-input-tokens:8192}")
    private int maxInputTokens;

    @Value("${sxw.agent.context.reserved-output-tokens:2048}")
    private int reservedOutputTokens;

    @Bean
    public ContextBudget contextBudget() {
        return ContextBudget.of(maxInputTokens, reservedOutputTokens);
    }
}
