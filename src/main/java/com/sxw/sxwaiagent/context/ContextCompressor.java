package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Context compressor.
 * <p>
 * Three-layer compression strategy:
 * 1. Large Tool Result compression (>500 chars keep only preview)
 * 2. Old history compression (beyond 10 turns compress to summary)
 * 3. Re-fetchable results keep only reference
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);
    private static final int TOOL_RESULT_PREVIEW_LIMIT = 200;
    private static final int HISTORY_SUMMARY_THRESHOLD = 10;

    /**
     * Compress static content (no degradation, truncation only).
     */
    public String compressStatic(String content, int maxTokens) {
        log.debug("Compressing static content to {} tokens", maxTokens);
        return truncateByTokens(content, maxTokens);
    }

    /**
     * Compress memory content.
     */
    public String compressMemory(String content, int maxTokens) {
        log.debug("Compressing memory to {} tokens", maxTokens);
        return truncateByTokens(content, maxTokens);
    }

    /**
     * Compress knowledge content (reduce topK).
     */
    public String compressKnowledge(String content, int maxTokens) {
        log.debug("Compressing knowledge to {} tokens", maxTokens);
        // Simple strategy: split by paragraphs, add one by one until budget exceeded
        String[] chunks = content.split("\n\n");
        StringBuilder sb = new StringBuilder();
        int currentTokens = 0;
        int charsPerToken = 4;

        for (String chunk : chunks) {
            int chunkTokens = (int) Math.ceil(chunk.length() / (double) charsPerToken);
            if (currentTokens + chunkTokens > maxTokens) {
                sb.append("\n...[knowledge truncated, ").append(chunks.length - sb.toString().split("\n\n").length).append(" items omitted]");
                break;
            }
            sb.append(chunk).append("\n\n");
            currentTokens += chunkTokens;
        }
        return sb.toString();
    }

    /**
     * Compress tool results (first-layer compression).
     */
    public String compressToolResults(String content, int maxTokens) {
        log.debug("Compressing tool results to {} tokens", maxTokens);

        // If single result exceeds 500 chars, keep only preview
        if (content.length() > 500) {
            String preview = content.substring(0, Math.min(TOOL_RESULT_PREVIEW_LIMIT, content.length()));
            content = preview + "\n...[tool result compressed, original length: " + content.length() + " chars]";
        }

        return truncateByTokens(content, maxTokens);
    }

    /**
     * Compress conversation history (second-layer compression).
     */
    public List<Message> compressHistory(List<Message> history, int maxTokens, TokenCounter tokenCounter) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // Calculate total tokens
        int totalTokens = history.stream()
                .mapToInt(msg -> tokenCounter.count(msg.getText()))
                .sum();

        if (totalTokens <= maxTokens) {
            return history;
        }

        // Over budget: keep recent N turns, compress old ones into summary
        List<Message> compressed = new ArrayList<>();

        if (history.size() > HISTORY_SUMMARY_THRESHOLD) {
            // Compress old messages
            List<Message> oldMessages = history.subList(0, history.size() - HISTORY_SUMMARY_THRESHOLD);
            String summary = buildHistorySummary(oldMessages);
            compressed.add(new org.springframework.ai.chat.messages.UserMessage("[history summary]\n" + summary));

            // Keep recent messages
            List<Message> recentMessages = history.subList(history.size() - HISTORY_SUMMARY_THRESHOLD, history.size());
            compressed.addAll(recentMessages);
        } else {
            // Discard from oldest until within budget
            int idx = 0;
            while (idx < history.size()) {
                List<Message> remaining = history.subList(idx, history.size());
                int remainingTokens = remaining.stream()
                        .mapToInt(msg -> tokenCounter.count(msg.getText()))
                        .sum();
                if (remainingTokens <= maxTokens) {
                    compressed.addAll(remaining);
                    break;
                }
                idx++;
            }
        }

        log.debug("History compressed: {} -> {} messages", history.size(), compressed.size());
        return compressed;
    }

    /**
     * Build history summary.
     */
    private String buildHistorySummary(List<Message> oldMessages) {
        // Simple strategy: extract keywords as topic summary
        StringBuilder sb = new StringBuilder();
        sb.append("First ").append(oldMessages.size()).append(" turns discussed the following:\n");

        int count = 0;
        for (Message msg : oldMessages) {
            if (count >= 5) break; // Extract at most 5 items
            String text = msg.getText();
            if (text != null && !text.isEmpty()) {
                String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                sb.append("- ").append(preview).append("\n");
                count++;
            }
        }

        return sb.toString();
    }

    /**
     * Truncate text by token count.
     */
    private String truncateByTokens(String text, int maxTokens) {
        if (text == null) return null;
        int charsPerToken = 4;
        int maxChars = maxTokens * charsPerToken;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated, original length: " + text.length() + " chars]";
    }
}
