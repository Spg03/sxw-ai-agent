package com.sxw.sxwaiagent.context;

import java.time.LocalDateTime;

/**
 * 上下文项记录
 * <p>
 * 记录每次模型调用时各区域的上下文内容，用于追踪和分析。
 * 对应数据库表 ai_context_item。
 */
public record ContextItem(
        Long id,
        String requestId,
        String traceId,
        int turn,
        Section section,
        String contentType,
        String contentPreview,
        String contentFull,
        int tokenCount,
        LocalDateTime createdAt
) {

    public enum Section {
        STATIC_RULES,      // 系统规则 + 工具规则 + 输出规则
        PROFILE_CONFIG,    // Profile 配置
        MEMORY_INDEX,      // 记忆摘要列表
        MEMORY_DETAIL,     // 记忆详情
        KNOWLEDGE,         // 知识库检索结果
        TOOL_RESULT,       // 工具执行结果
        HISTORY,           // 对话历史
        USER_MESSAGE       // 用户消息
    }

    public static ContextItem of(
            String requestId,
            String traceId,
            int turn,
            Section section,
            String contentFull,
            int tokenCount
    ) {
        String preview = contentFull != null && contentFull.length() > 500
                ? contentFull.substring(0, 500) + "..."
                : contentFull;

        return new ContextItem(
                null,
                requestId,
                traceId,
                turn,
                section,
                "text",
                preview,
                contentFull,
                tokenCount,
                LocalDateTime.now()
        );
    }
}
