package com.sxw.sxwaiagent.conversation;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sxw.sxwaiagent.context.ContextBudget;
import com.sxw.sxwaiagent.context.TokenCounter;
import com.sxw.sxwaiagent.memory.ConversationSummaryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a replayable context from versioned PostgreSQL state and complete turns. */
@Service
public class ConversationContextService {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextService.class);
    private final JdbcTemplate jdbc;
    private final ConversationSummaryService summaries;
    private final TokenCounter tokens;
    private final ContextBudget budget;

    @Value("${sxw.agent.memory.max-recent-turns:10}")
    private int maxRecentTurns;
    @Value("${sxw.agent.memory.max-recent-events:40}")
    private int maxRecentMessages;

    public ConversationContextService(JdbcTemplate jdbc, ConversationSummaryService summaries,
                                      TokenCounter tokens, ContextBudget budget) {
        this.jdbc = jdbc;
        this.summaries = summaries;
        this.tokens = tokens;
        this.budget = budget;
    }

    public ContextSlice load(long userId, String conversationId, String currentRequestId) {
        return loadInternal(userId, conversationId, currentRequestId);
    }

    public ContextSlice prepare(long userId, String conversationId, String currentRequestId,
                                String currentMessage) {
        ContextSlice slice = loadInternal(userId, conversationId, currentRequestId);
        int estimated = budget.staticBudget() + tokens.count(currentMessage) + tokens.count(slice.summary())
                + tokens.count(slice.workingMemory())
                + slice.messages().stream().mapToInt(message -> tokens.count(message.getText())).sum();
        if (summaries.mustSummarize(estimated, budget.availableTokens())) {
            try {
                summaries.materializeLatest(conversationId);
                return loadInternal(userId, conversationId, currentRequestId);
            } catch (Exception summaryError) {
                log.warn("Synchronous summary failed for {}; falling back to token-aware trimming: {}",
                        conversationId, summaryError.getMessage());
                summaries.requestIfNeeded(conversationId);
                return slice;
            }
        }
        if (summaries.shouldPrewarm(estimated, budget.availableTokens())) {
            summaries.requestIfNeeded(conversationId);
        }
        return slice;
    }

    private ContextSlice loadInternal(long userId, String conversationId, String currentRequestId) {
        Integer owner = jdbc.query("SELECT 1 FROM ai_conversation WHERE conversation_id=? AND user_id=?",
                rs -> rs.next() ? 1 : null, conversationId, userId);
        if (owner == null) {
            throw new IllegalArgumentException("Conversation not found");
        }

        SummaryVersion summary = jdbc.query("""
                SELECT version_no,covered_end_sequence,content
                  FROM ai_conversation_summary_version
                 WHERE conversation_id=? AND status='SUCCESS'
                 ORDER BY version_no DESC LIMIT 1
                """, rs -> rs.next()
                ? new SummaryVersion(rs.getInt(1), rs.getLong(2), rs.getString(3))
                : SummaryVersion.empty(), conversationId);

        WorkingMemoryVersion working = jdbc.query("""
                SELECT version_no,materialized_until_sequence,goal,task_status,plan_id,plan_version,
                       constraints::text,open_questions::text,next_actions::text
                  FROM ai_working_memory_version
                 WHERE conversation_id=? ORDER BY version_no DESC LIMIT 1
                """, rs -> rs.next()
                ? new WorkingMemoryVersion(rs.getInt(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), (Integer) rs.getObject(6), rs.getString(7), rs.getString(8), rs.getString(9))
                : WorkingMemoryVersion.empty(), conversationId);

        List<EventRow> rows = jdbc.query("""
                SELECT sequence_no,turn_id,role,message_type,content
                  FROM ai_conversation_message
                 WHERE conversation_id=?
                   AND sequence_no>?
                   AND processing_status='COMPLETED'
                   AND (request_id IS NULL OR request_id<>?)
                 ORDER BY sequence_no
                """, (rs, rowNum) -> new EventRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), conversationId, summary.coveredEndSequence(),
                currentRequestId == null ? "" : currentRequestId);

        List<List<EventRow>> completeTurns = completeTurns(rows);
        int from = Math.max(0, completeTurns.size() - Math.max(1, maxRecentTurns));
        List<EventRow> selected = new ArrayList<>();
        for (int i = from; i < completeTurns.size(); i++) {
            selected.addAll(completeTurns.get(i));
        }
        while (selected.size() > Math.max(2, maxRecentMessages) && !selected.isEmpty()) {
            String oldestTurn = selected.getFirst().turnId();
            selected.removeIf(row -> sameTurn(oldestTurn, row.turnId()));
        }

        List<Message> messages = selected.stream().map(this::toMessage).toList();
        long recentStart = selected.isEmpty() ? 0 : selected.getFirst().sequenceNo();
        long recentEnd = selected.isEmpty() ? 0 : selected.getLast().sequenceNo();
        return new ContextSlice(messages, summary.content(), summary.versionNo(), summary.coveredEndSequence(),
                working.render(), working.versionNo(), working.materializedUntilSequence(), recentStart, recentEnd);
    }

    private List<List<EventRow>> completeTurns(List<EventRow> rows) {
        Map<String, List<EventRow>> grouped = new LinkedHashMap<>();
        for (EventRow row : rows) {
            String key = row.turnId() == null ? "sequence:" + row.sequenceNo() : row.turnId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream()
                .filter(turn -> turn.stream().anyMatch(row -> "USER".equals(row.messageType()))
                        && turn.stream().anyMatch(row -> "ASSISTANT".equals(row.messageType())))
                .toList();
    }

    private Message toMessage(EventRow row) {
        if ("USER".equals(row.role())) {
            return new UserMessage(row.content());
        }
        if ("TOOL_RESULT".equals(row.messageType()) || "TOOL".equals(row.role())) {
            return new AssistantMessage("<historical_tool_data>\n" + row.content()
                    + "\n</historical_tool_data>");
        }
        return new AssistantMessage(row.content());
    }

    private static boolean sameTurn(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record EventRow(long sequenceNo, String turnId, String role, String messageType, String content) { }
    private record SummaryVersion(int versionNo, long coveredEndSequence, String content) {
        static SummaryVersion empty() { return new SummaryVersion(0, 0, ""); }
    }
    private record WorkingMemoryVersion(int versionNo, long materializedUntilSequence, String goal,
                                        String taskStatus, String planId, Integer planVersion,
                                        String constraints, String openQuestions, String nextActions) {
        static WorkingMemoryVersion empty() {
            return new WorkingMemoryVersion(0, 0, null, null, null, null, "[]", "[]", "[]");
        }
        String render() {
            if (versionNo == 0) return "";
            return "goal: " + value(goal) + "\nstatus: " + value(taskStatus)
                    + "\nplanRef: " + value(planId) + (planVersion == null ? "" : "@" + planVersion)
                    + "\nconstraints: " + value(constraints)
                    + "\nopenQuestions: " + value(openQuestions)
                    + "\nnextActions: " + value(nextActions);
        }
        private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
    }

    public record ContextSlice(List<Message> messages, String summary, int summaryVersion,
                               long summaryEndSequence, String workingMemory, int workingMemoryVersion,
                               long workingMemoryUntilSequence, long recentStartSequence,
                               long recentEndSequence) {
        public void contributeTo(Map<String, Object> metadata) {
            metadata.put("conversationSummary", summary == null ? "" : summary);
            metadata.put("summaryVersion", summaryVersion);
            metadata.put("summaryEndSequence", summaryEndSequence);
            metadata.put("workingMemory", workingMemory == null ? "" : workingMemory);
            metadata.put("workingMemoryVersion", workingMemoryVersion);
            metadata.put("workingMemoryUntilSequence", workingMemoryUntilSequence);
            metadata.put("recentStartSequence", recentStartSequence);
            metadata.put("recentEndSequence", recentEndSequence);
        }
    }
}
