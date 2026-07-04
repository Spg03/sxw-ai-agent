package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token 计数器
 * <p>
 * 估算文本的 Token 数量。当前使用简单的字符数 / 4 近似算法，
 * 后续可替换为 tiktoken 或模型实际 tokenizer。
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * 估算 Token 数量
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * 按 Token 预算截断文本
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
        return text.substring(0, maxChars) + "\n...[已截断，原长度: " + text.length() + " 字符]";
    }
}
