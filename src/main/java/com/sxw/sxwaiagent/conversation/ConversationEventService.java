package com.sxw.sxwaiagent.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Appends immutable conversation events and owns the single-active-turn invariant.
 * PostgreSQL is the source of truth for both idempotency and execution ownership.
 */
@Service
public class ConversationEventService {
    private static final Duration STALE_TURN_AFTER = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConversationEventService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Atomically acquires the conversation and appends the USER event once. */
    @Transactional
    public TurnStartResult startTurn(long userId, String conversationId, String requestId,
                                     String content, Map<String, Object> metadata) {
        ConversationLock lock = lockConversation(userId, conversationId);
        Optional<RequestState> existing = requestState(conversationId, requestId);
        if (existing.isPresent()) {
            RequestState state = existing.get();
            return new TurnStartResult(
                    state.assistantAnswer() == null ? TurnStartStatus.DUPLICATE_IN_PROGRESS : TurnStartStatus.DUPLICATE_COMPLETED,
                    state.turnId(), state.userMessageId(), state.assistantAnswer(), state.processingStatus());
        }

        if (lock.activeRequestId() != null && !lock.activeRequestId().equals(requestId)) {
            boolean stale = lock.activeStartedAt() != null
                    && lock.activeStartedAt().plus(STALE_TURN_AFTER).isBefore(Instant.now());
            if (!stale) {
                return new TurnStartResult(TurnStartStatus.BUSY, lock.activeTurnId(), null, null, "PROCESSING");
            }
            jdbc.update("UPDATE ai_conversation_message SET processing_status='FAILED' "
                            + "WHERE conversation_id=? AND request_id=? AND message_type='USER' "
                            + "AND processing_status IN ('RECEIVED','PROCESSING')",
                    conversationId, lock.activeRequestId());
        }

        String turnId = "turn_" + requestId;
        jdbc.update("UPDATE ai_conversation SET active_turn_id=?,active_request_id=?,active_turn_started_at=NOW(),"
                        + "runtime_version=runtime_version+1,updated_at=NOW() WHERE conversation_id=? AND user_id=?",
                turnId, requestId, conversationId, userId);
        EventAppendResult userEvent = appendLocked(conversationId, turnId, requestId, "USER", "USER",
                content, "PROCESSING", metadata);
        jdbc.update("UPDATE ai_conversation SET title=CASE WHEN title IN ('New conversation','新建对话') "
                        + "THEN LEFT(?,160) ELSE title END WHERE conversation_id=? AND user_id=?",
                content.replaceAll("\\s+", " "), conversationId, userId);
        return new TurnStartResult(TurnStartStatus.STARTED, turnId, userEvent.messageId(), null, "PROCESSING");
    }

    /** Appends a final response once and releases the active turn. */
    @Transactional
    public EventAppendResult completeTurn(String conversationId, String requestId, String answer,
                                          Map<String, Object> metadata) {
        lockConversation(conversationId);
        String turnId = jdbc.query("SELECT turn_id FROM ai_conversation_message WHERE conversation_id=? "
                        + "AND request_id=? AND message_type='USER' LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, conversationId, requestId);
        if (turnId == null) {
            throw new IllegalArgumentException("Conversation request not found");
        }
        EventAppendResult result = appendLocked(conversationId, turnId, requestId, "ASSISTANT", "ASSISTANT",
                answer == null ? "" : answer, "COMPLETED", metadata);
        jdbc.update("UPDATE ai_conversation_message SET processing_status='COMPLETED' "
                        + "WHERE conversation_id=? AND request_id=? AND message_type='USER'",
                conversationId, requestId);
        releaseLocked(conversationId, requestId);
        return result;
    }

    @Transactional
    public void failTurn(String conversationId, String requestId, String status, String errorCategory) {
        lockConversation(conversationId);
        String normalized = switch (status) {
            case "CANCELLED", "REJECTED_TOO_LONG" -> status;
            default -> "FAILED";
        };
        jdbc.update("UPDATE ai_conversation_message SET processing_status=?, "
                        + "metadata=metadata || jsonb_build_object('errorCategory', ?) "
                        + "WHERE conversation_id=? AND request_id=? AND message_type='USER' "
                        + "AND processing_status IN ('RECEIVED','PROCESSING')",
                normalized, errorCategory == null ? "UNKNOWN" : errorCategory, conversationId, requestId);
        releaseLocked(conversationId, requestId);
    }

    /** General append API used for TOOL and runtime events. */
    @Transactional
    public EventAppendResult append(String conversationId, String turnId, String requestId, String role,
                                    String type, String content, String status, Map<String, Object> metadata) {
        lockConversation(conversationId);
        return appendLocked(conversationId, turnId, requestId, role, type, content, status, metadata);
    }

    @Transactional
    public EventAppendResult appendTool(String conversationId, String turnId, String requestId, String role,
                                        String type, String toolCallId, String toolName, String content,
                                        Map<String, Object> metadata) {
        lockConversation(conversationId);
        EventAppendResult result = appendLocked(conversationId, turnId, requestId, role, type,
                content, "COMPLETED", metadata);
        jdbc.update("UPDATE ai_conversation_message SET tool_call_id=?,tool_name=? WHERE id=?",
                toolCallId, toolName, result.messageId());
        return result;
    }

    public Optional<RequestState> requestState(String conversationId, String requestId) {
        List<RequestState> states = jdbc.query("""
                SELECT u.id,u.turn_id,u.processing_status,a.content
                  FROM ai_conversation_message u
                  LEFT JOIN ai_conversation_message a
                    ON a.conversation_id=u.conversation_id
                   AND a.request_id=u.request_id
                   AND a.message_type='ASSISTANT'
                 WHERE u.conversation_id=? AND u.request_id=? AND u.message_type='USER'
                 LIMIT 1
                """, (rs, rowNum) -> new RequestState(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), conversationId, requestId);
        return states.stream().findFirst();
    }

    private EventAppendResult appendLocked(String conversationId, String turnId, String requestId, String role,
                                           String type, String content, String status, Map<String, Object> metadata) {
        if (requestId != null && ("USER".equals(type) || "ASSISTANT".equals(type))) {
            Optional<Long> existing = jdbc.query("SELECT id FROM ai_conversation_message WHERE conversation_id=? "
                            + "AND request_id=? AND message_type=?",
                    rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), conversationId, requestId, type);
            if (existing.isPresent()) {
                return new EventAppendResult(existing.get(), true);
            }
        }
        Long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM ai_conversation_message "
                + "WHERE conversation_id=?", Long.class, conversationId);
        long id = jdbc.queryForObject("""
                INSERT INTO ai_conversation_message(
                    conversation_id,sequence_no,turn_id,request_id,role,message_type,
                    content,processing_status,content_hash,metadata)
                VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)
                RETURNING id
                """, Long.class, conversationId, sequence, turnId, requestId, role, type,
                content, status, sha256(content), write(metadata));
        jdbc.update("UPDATE ai_conversation SET updated_at=NOW() WHERE conversation_id=?", conversationId);
        return new EventAppendResult(id, false);
    }

    private ConversationLock lockConversation(long userId, String conversationId) {
        List<ConversationLock> rows = jdbc.query("SELECT active_turn_id,active_request_id,active_turn_started_at "
                        + "FROM ai_conversation WHERE conversation_id=? AND user_id=? FOR UPDATE",
                (rs, rowNum) -> new ConversationLock(rs.getString(1), rs.getString(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()), conversationId, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Conversation not found");
        }
        return rows.getFirst();
    }

    private void lockConversation(String conversationId) {
        Integer found = jdbc.query("SELECT 1 FROM ai_conversation WHERE conversation_id=? FOR UPDATE",
                rs -> rs.next() ? 1 : null, conversationId);
        if (found == null) {
            throw new IllegalArgumentException("Conversation not found");
        }
    }

    private void releaseLocked(String conversationId, String requestId) {
        jdbc.update("UPDATE ai_conversation SET active_turn_id=NULL,active_request_id=NULL,active_turn_started_at=NULL,"
                        + "runtime_version=runtime_version+1,updated_at=NOW() WHERE conversation_id=? AND active_request_id=?",
                conversationId, requestId);
    }

    private String write(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Conversation metadata is not serializable", e);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : bytes) {
                out.append(String.format("%02x", item));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public enum TurnStartStatus { STARTED, DUPLICATE_IN_PROGRESS, DUPLICATE_COMPLETED, BUSY }

    public record TurnStartResult(TurnStartStatus status, String turnId, Long userMessageId,
                                  String existingAnswer, String processingStatus) {
        public boolean started() { return status == TurnStartStatus.STARTED; }
        public boolean duplicateCompleted() { return status == TurnStartStatus.DUPLICATE_COMPLETED; }
    }

    public record RequestState(long userMessageId, String turnId, String processingStatus,
                               String assistantAnswer) { }
    public record EventAppendResult(long messageId, boolean duplicate) { }
    private record ConversationLock(String activeTurnId, String activeRequestId, Instant activeStartedAt) { }
}
