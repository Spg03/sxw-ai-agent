package com.sxw.sxwaiagent.conversation;

import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.sxw.sxwaiagent.memory.ConversationSummaryService;
import com.sxw.sxwaiagent.memory.AgentRunSnapshotService;
import com.sxw.sxwaiagent.memory.MemoryRetrievalSnapshotStore;
import com.sxw.sxwaiagent.memory.UserMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ConversationRunListener {
    private static final Logger log = LoggerFactory.getLogger(ConversationRunListener.class);
    private final ConversationEventService events;
    private final ConversationSummaryService summaries;
    private final AgentRunSnapshotService snapshots;
    private final UserMemoryService memories;
    private final MemoryRetrievalSnapshotStore retrievalSnapshots;

    public ConversationRunListener(ConversationEventService events, ConversationSummaryService summaries,
                                   AgentRunSnapshotService snapshots, UserMemoryService memories,
                                   MemoryRetrievalSnapshotStore retrievalSnapshots) {
        this.events = events;
        this.summaries = summaries;
        this.snapshots = snapshots;
        this.memories = memories;
        this.retrievalSnapshots = retrievalSnapshots;
    }
    @EventListener
    public void onCompleted(AgentRunCompletedEvent event) {
        if (event.getChatId() == null || event.getChatId().isBlank() || event.getResponse() == null || event.getResponse().answer() == null) return;
        try {
            events.completeTurn(event.getChatId(), event.getRequestId(), event.getResponse().answer(),
                    java.util.Map.of("traceId", event.getTraceId(), "latencyMs", event.getResponse().latencyMs()));
            snapshots.completeByRequest(event.getRequestId(), "COMPLETED", null);
            summaries.requestIfNeeded(event.getChatId());
            persistExplicitMemory(event);
        }
        catch (Exception error) {
            log.error("Unable to persist completed conversation run {}: {}",
                    event.getRequestId(), error.getMessage(), error);
        }
        finally {
            retrievalSnapshots.invalidate(event.getRequestId());
        }
    }

    private void persistExplicitMemory(AgentRunCompletedEvent event) {
        if (!event.isMemoryWriteEnabled() || event.getUserId() == null) return;
        String content = extractExplicitMemory(event.getUserMessage());
        if (content == null) return;
        try {
            memories.explicit(event.getUserId(), event.getChatId(), content, "GLOBAL", null);
        } catch (IllegalArgumentException rejected) {
            log.info("Explicit memory rejected for request {}: {}", event.getRequestId(), rejected.getMessage());
        }
    }

    static String extractExplicitMemory(String message) {
        if (message == null || message.isBlank()) return null;
        String normalized = message.trim();
        java.util.regex.Matcher chinese = java.util.regex.Pattern
                .compile("(?is)^(?:请你?|麻烦你?)?(?:帮我)?记住(?:一下|：|:|，|,|这件事)?\\s*(.+)$")
                .matcher(normalized);
        if (chinese.matches()) return cleanMemoryText(chinese.group(1));
        java.util.regex.Matcher english = java.util.regex.Pattern
                .compile("(?is)^(?:please\\s+)?remember(?:\\s+that)?[,:]?\\s+(.+)$")
                .matcher(normalized);
        return english.matches() ? cleanMemoryText(english.group(1)) : null;
    }

    private static String cleanMemoryText(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        while (cleaned.endsWith("。") || cleaned.endsWith("！") || cleaned.endsWith("!")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned.isBlank() || cleaned.length() > 500 ? null : cleaned;
    }
}
