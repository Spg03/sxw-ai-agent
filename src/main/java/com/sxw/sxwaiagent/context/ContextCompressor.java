package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器
 * <p>
 * 三层压缩策略：
 * 1. 大 Tool Result 压缩（>500 字符只保留 preview）
 * 2. 旧历史消息压缩（超 10 轮压缩成 summary）
 * 3. 可重新获取的结果只保留引用
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);
    private static final int TOOL_RESULT_PREVIEW_LIMIT = 200;
    private static final int HISTORY_SUMMARY_THRESHOLD = 10;

    /**
     * 压缩静态内容（不降级，仅截断）
     */
    public String compressStatic(String content, int maxTokens) {
        log.debug("Compressing static content to {} tokens", maxTokens);
        return truncateByTokens(content, maxTokens);
    }

    /**
     * 压缩记忆内容
     */
    public String compressMemory(String content, int maxTokens) {
        log.debug("Compressing memory to {} tokens", maxTokens);
        return truncateByTokens(content, maxTokens);
    }

    /**
     * 压缩知识库内容（减少 topK）
     */
    public String compressKnowledge(String content, int maxTokens) {
        log.debug("Compressing knowledge to {} tokens", maxTokens);
        // 简单策略：按段落分割，逐个添加直到超预算
        String[] chunks = content.split("\n\n");
        StringBuilder sb = new StringBuilder();
        int currentTokens = 0;
        int charsPerToken = 4;

        for (String chunk : chunks) {
            int chunkTokens = (int) Math.ceil(chunk.length() / (double) charsPerToken);
            if (currentTokens + chunkTokens > maxTokens) {
                sb.append("\n...[知识库内容已截断，剩余 ").append(chunks.length - sb.toString().split("\n\n").length).append(" 条未展示]");
                break;
            }
            sb.append(chunk).append("\n\n");
            currentTokens += chunkTokens;
        }
        return sb.toString();
    }

    /**
     * 压缩工具结果（第一层压缩）
     */
    public String compressToolResults(String content, int maxTokens) {
        log.debug("Compressing tool results to {} tokens", maxTokens);

        // 如果单个结果超过 500 字符，只保留 preview
        if (content.length() > 500) {
            String preview = content.substring(0, Math.min(TOOL_RESULT_PREVIEW_LIMIT, content.length()));
            content = preview + "\n...[工具结果已压缩，原长度: " + content.length() + " 字符]";
        }

        return truncateByTokens(content, maxTokens);
    }

    /**
     * 压缩对话历史（第二层压缩）
     */
    public List<Message> compressHistory(List<Message> history, int maxTokens, TokenCounter tokenCounter) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // 计算总 token
        int totalTokens = history.stream()
                .mapToInt(msg -> tokenCounter.count(msg.getText()))
                .sum();

        if (totalTokens <= maxTokens) {
            return history;
        }

        // 超预算：保留最近的 N 轮，旧的压缩成 summary
        List<Message> compressed = new ArrayList<>();

        if (history.size() > HISTORY_SUMMARY_THRESHOLD) {
            // 压缩旧消息
            List<Message> oldMessages = history.subList(0, history.size() - HISTORY_SUMMARY_THRESHOLD);
            String summary = buildHistorySummary(oldMessages);
            compressed.add(new org.springframework.ai.chat.messages.UserMessage("[历史摘要]\n" + summary));

            // 保留最近消息
            List<Message> recentMessages = history.subList(history.size() - HISTORY_SUMMARY_THRESHOLD, history.size());
            compressed.addAll(recentMessages);
        } else {
            // 从最早的消息开始丢弃，直到不超预算
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
     * 构建历史消息摘要
     */
    private String buildHistorySummary(List<Message> oldMessages) {
        // 简单策略：提取关键词作为主题摘要
        StringBuilder sb = new StringBuilder();
        sb.append("前 ").append(oldMessages.size()).append(" 轮对话讨论了以下内容：\n");

        int count = 0;
        for (Message msg : oldMessages) {
            if (count >= 5) break; // 最多提取 5 条
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
     * 按 Token 数截断文本
     */
    private String truncateByTokens(String text, int maxTokens) {
        if (text == null) return null;
        int charsPerToken = 4;
        int maxChars = maxTokens * charsPerToken;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[已截断，原长度: " + text.length() + " 字符]";
    }
}
