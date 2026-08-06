package com.sxw.sxwaiagent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC 持久化的 ChatMemoryRepository，后端为 PostgreSQL。
 * <p>
 * 将 Spring AI 的 Message 序列化为 JSON 存入 ai_chat_memory 表，
 * save 操作采用 DELETE + INSERT 全量替换语义，并在同一事务内执行。
 */
@Repository
@Slf4j
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<AssistantMessage.ToolCall>> TOOL_CALLS_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<ToolResponseMessage.ToolResponse>> TOOL_RESPONSES_TYPE =
            new TypeReference<>() {};

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ────────────────────────── ChatMemoryRepository 接口 ──────────────────────────

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM ai_chat_memory", String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query("""
                SELECT content, type, "timestamp"
                FROM ai_chat_memory
                WHERE conversation_id = ?
                ORDER BY "timestamp" ASC
                """,
                (rs, rowNum) -> {
                    String contentJson = rs.getString("content");
                    String typeStr = rs.getString("type");
                    return deserializeMessage(typeStr, contentJson);
                },
                conversationId).stream()
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jdbcTemplate.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO ai_chat_memory (conversation_id, content, type, "timestamp")
                VALUES (?, ?, ?, ?)
                """, messages, messages.size(),
                (ps, message) -> {
                    ps.setString(1, conversationId);
                    ps.setString(2, serializeMessage(message));
                    ps.setString(3, message.getMessageType().name());
                    ps.setTimestamp(4, Timestamp.from(now));
                });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
        if (deleted > 0) {
            log.debug("Deleted {} chat memory records for conversationId={}", deleted, conversationId);
        }
    }

    // ────────────────────────── 序列化 / 反序列化 ──────────────────────────

    /**
     * 将 Message 序列化为 JSON 字符串存入 content 列。
     * <ul>
     *   <li>USER / SYSTEM → {"text": "..."}</li>
     *   <li>ASSISTANT → {"text": "...", "toolCalls": [...]}（toolCalls 可选）</li>
     *   <li>TOOL → {"responses": [...]}</li>
     * </ul>
     */
    private String serializeMessage(Message message) {
        try {
            if (message instanceof AssistantMessage assistant) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("text", assistant.getText());
                if (assistant.hasToolCalls()) {
                    map.put("toolCalls", assistant.getToolCalls());
                }
                return objectMapper.writeValueAsString(map);
            } else if (message instanceof ToolResponseMessage toolResponse) {
                Map<String, Object> map = Map.of("responses", toolResponse.getResponses());
                return objectMapper.writeValueAsString(map);
            } else {
                // UserMessage / SystemMessage
                Map<String, Object> map = Map.of("text", message.getText());
                return objectMapper.writeValueAsString(map);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message type={}", message.getMessageType(), e);
            throw new IllegalStateException("Message serialization failed", e);
        }
    }

    /**
     * 根据 type 列和 content JSON 反序列化为对应 Message 子类。
     * 失败时返回 Optional.empty()，由调用方过滤，不注入占位消息污染上下文。
     */
    private Optional<Message> deserializeMessage(String typeStr, String contentJson) {
        try {
            MessageType type = MessageType.valueOf(typeStr);
            Map<String, Object> map = objectMapper.readValue(contentJson,
                    new TypeReference<Map<String, Object>>() {});
            return Optional.of(switch (type) {
                case USER -> new UserMessage((String) map.get("text"));
                case SYSTEM -> new SystemMessage((String) map.get("text"));
                case ASSISTANT -> {
                    String text = (String) map.get("text");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rawCalls = (List<Map<String, Object>>) map.get("toolCalls");
                    List<AssistantMessage.ToolCall> toolCalls = deserializeToolCalls(rawCalls);
                    yield new AssistantMessage(text != null ? text : "", Map.of(), toolCalls);
                }
                case TOOL -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rawResponses =
                            (List<Map<String, Object>>) map.get("responses");
                    List<ToolResponseMessage.ToolResponse> responses =
                            deserializeToolResponses(rawResponses);
                    yield new ToolResponseMessage(responses);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize chat memory record (skipped): type={}", typeStr, e);
            return Optional.empty();
        }
    }

    private List<AssistantMessage.ToolCall> deserializeToolCalls(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String json = objectMapper.writeValueAsString(raw);
            List<AssistantMessage.ToolCall> calls = objectMapper.readValue(json, TOOL_CALLS_TYPE);
            return calls != null ? calls : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize toolCalls", e);
            return Collections.emptyList();
        }
    }

    private List<ToolResponseMessage.ToolResponse> deserializeToolResponses(
            List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String json = objectMapper.writeValueAsString(raw);
            List<ToolResponseMessage.ToolResponse> responses =
                    objectMapper.readValue(json, TOOL_RESPONSES_TYPE);
            return responses != null ? responses : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize toolResponses", e);
            return Collections.emptyList();
        }
    }
}
