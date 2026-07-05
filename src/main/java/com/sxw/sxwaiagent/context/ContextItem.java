package com.sxw.sxwaiagent.context;

import java.time.LocalDateTime;

/**
 * Context item record.
 * <p>
 * Records context content for each region during model calls, used for tracking and analysis.
 * Maps to database table ai_context_item.
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
        STATIC_RULES,      // System rules + Tool rules + Output rules
        PROFILE_CONFIG,    // Profile configuration
        MEMORY_INDEX,      // Memory summary list
        MEMORY_DETAIL,     // Memory details
        KNOWLEDGE,         // Knowledge retrieval results
        TOOL_RESULT,       // Tool execution results
        HISTORY,           // Conversation history
        USER_MESSAGE       // User message
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
