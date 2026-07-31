package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token counter.
 * <p>
 * Estimate token count for text using a weighted character approach:
 * - English/Latin: ~4 chars per token (chars / 4)
 * - CJK (Chinese/Japanese/Korean): ~1.5 tokens per char (chars * 1.5)
 * <p>
 * Can be replaced with tiktoken or the model's actual tokenizer later.
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;
    private static final double CJK_TOKENS_PER_CHAR = 1.5;

    /**
     * Estimate token count with CJK-aware weighting.
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkCount = 0;
        int totalChars = text.length();
        for (int i = 0; i < totalChars; i++) {
            if (isCjk(text.charAt(i))) {
                cjkCount++;
            }
        }
        int nonCjkCount = totalChars - cjkCount;
        double tokens = (nonCjkCount / ENGLISH_CHARS_PER_TOKEN) + (cjkCount * CJK_TOKENS_PER_CHAR);
        return (int) Math.ceil(tokens);
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
        // Estimate chars per token ratio from the text itself
        double avgCharsPerToken = (double) text.length() / currentTokens;
        int maxChars = (int) (maxTokens * avgCharsPerToken);
        if (maxChars >= text.length()) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated, original length: " + text.length() + " chars]";
    }

    /**
     * Detect CJK (Chinese/Japanese/Korean) characters by Unicode range.
     */
    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
