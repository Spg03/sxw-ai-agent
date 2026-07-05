package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token counter.
 * <p>
 * Estimate token count for text. Currently uses a simple chars/4 approximation;
 * can be replaced with tiktoken or the model's actual tokenizer later.
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Estimate token count.
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Truncate text by token budget.
     */
    public String truncate(String text, int maxTokens) {
        if (text == null) return null;
        int currentTokens = count(text);
        if (currentTokens <= maxTokens) {
            return text;
        }
        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        if (maxChars >= text.length()) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated, original length: " + text.length() + " chars]";
    }
}
