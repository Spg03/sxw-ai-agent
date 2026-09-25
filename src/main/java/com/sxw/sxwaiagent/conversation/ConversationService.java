package com.sxw.sxwaiagent.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.attachment.AttachmentService;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.memory.UserMemoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private static final int RECENT_MESSAGE_LIMIT = 20;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AttachmentService attachments;
    private final UserMemoryService memories;

    public ConversationService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper,
                               AttachmentService attachments, UserMemoryService memories) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.attachments = attachments;
        this.memories = memories;
    }

    @Transactional
    public ConversationSummary create(long userId, AgentProfileCode profile, String title) {
        String id = "chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update("INSERT INTO ai_conversation(conversation_id,user_id,title,profile_code) VALUES (?,?,?,?)", id, userId,
                title == null || title.isBlank() ? "New conversation" : title.trim(), profile.name());
        return get(userId, id);
    }

    @Transactional
    public ConversationSummary ensure(long userId, String id, AgentProfileCode profile) {
        jdbc.update("INSERT INTO ai_conversation(conversation_id,user_id,title,profile_code) VALUES (?,?,?,?) ON CONFLICT (conversation_id) DO NOTHING", id, userId, "New conversation", profile.name());
        return get(userId, id);
    }

    public List<ConversationSummary> list(long userId, int limit) {
        return jdbc.query("SELECT * FROM ai_conversation WHERE user_id=? ORDER BY pinned DESC,updated_at DESC LIMIT ?", this::map, userId, Math.min(Math.max(limit, 1), 100));
    }

    public ConversationSummary get(long userId, String id) {
        List<ConversationSummary> result = jdbc.query("SELECT * FROM ai_conversation WHERE conversation_id=? AND user_id=?", this::map, id, userId);
        if (result.isEmpty()) throw new IllegalArgumentException("Conversation not found");
        return result.getFirst();
    }

    public List<ConversationMessage> messages(long userId, String id, int limit) {
        get(userId, id);
        return jdbc.query("SELECT * FROM (SELECT * FROM ai_conversation_message WHERE conversation_id=? "
                        + "AND message_type IN ('USER','ASSISTANT') ORDER BY sequence_no DESC LIMIT ?) m ORDER BY sequence_no",
                (rs, n) -> new ConversationMessage(rs.getLong("id"), rs.getString("role"), rs.getString("content"), rs.getTimestamp("created_at").toInstant()),
                id, Math.min(Math.max(limit, 1), 200));
    }

    public List<ConversationMessage> recentMessages(long userId, String id) {
        get(userId, id);
        try {
            List<String> cached = redis.opsForList().range(cacheKey(userId, id), 0, -1);
            if (cached != null && !cached.isEmpty()) {
                List<ConversationMessage> result = new ArrayList<>();
                for (String item : cached) result.add(objectMapper.readValue(item, ConversationMessage.class));
                return result;
            }
        } catch (Exception ignored) { }
        List<ConversationMessage> result = messages(userId, id, RECENT_MESSAGE_LIMIT);
        for (ConversationMessage message : result) cache(userId, id, message.role(), message.content(), message.id(), message.createdAt());
        return result;
    }

    public ConversationSummary update(long userId, String id, String title, AgentProfileCode profile, Boolean pinned) {
        get(userId, id);
        jdbc.update("UPDATE ai_conversation SET title=COALESCE(?,title),profile_code=COALESCE(?,profile_code),pinned=COALESCE(?,pinned),updated_at=NOW() WHERE conversation_id=? AND user_id=?",
                title == null ? null : title.trim(), profile == null ? null : profile.name(), pinned, id, userId);
        return get(userId, id);
    }

    public void delete(long userId, String id) { delete(userId, id, false); }

    @Transactional
    public void delete(long userId, String id, boolean purgeInferredMemories) {
        get(userId, id);
        jdbc.queryForList("SELECT user_id FROM ai_conversation WHERE conversation_id=? AND user_id=? FOR UPDATE", Long.class, id, userId);
        if (purgeInferredMemories) memories.purgeInferredForConversation(userId, id);
        attachments.deleteConversationAttachments(userId, id);
        if (jdbc.update("DELETE FROM ai_conversation WHERE conversation_id=? AND user_id=?", id, userId) == 0) throw new IllegalArgumentException("Conversation not found");
        try { redis.delete(cacheKey(userId, id)); } catch (Exception ignored) { }
    }

    public DeletionPreview deletionPreview(long userId, String id) {
        get(userId, id);
        Long messages = jdbc.queryForObject("SELECT count(*) FROM ai_conversation_message WHERE conversation_id=?", Long.class, id);
        Long attachmentCount = jdbc.queryForObject("SELECT count(*) FROM ai_chat_attachment WHERE user_id=? AND conversation_id=?", Long.class, userId, id);
        return new DeletionPreview(messages == null ? 0 : messages,
                attachmentCount == null ? 0 : attachmentCount,
                memories.inferredCountForConversation(userId, id));
    }

    @Transactional
    public void clearMessages(long userId, String id) {
        get(userId, id);
        jdbc.update("DELETE FROM ai_agent_run WHERE conversation_id=?", id);
        jdbc.update("DELETE FROM ai_working_memory_version WHERE conversation_id=?", id);
        jdbc.update("DELETE FROM ai_conversation_summary_version WHERE conversation_id=?", id);
        jdbc.update("DELETE FROM ai_conversation_message WHERE conversation_id=?", id);
        jdbc.update("UPDATE ai_conversation SET rolling_summary=NULL,summary_message_count=0,"
                + "active_turn_id=NULL,active_request_id=NULL,active_turn_started_at=NULL,updated_at=NOW() "
                + "WHERE conversation_id=? AND user_id=?", id, userId);
        try { redis.delete(cacheKey(userId, id)); } catch (Exception ignored) { }
    }

    private void cache(long userId, String id, String role, String content, long messageId, Instant createdAt) {
        try {
            String key = cacheKey(userId, id);
            redis.opsForList().rightPush(key, objectMapper.writeValueAsString(new ConversationMessage(messageId, role, content, createdAt)));
            redis.opsForList().trim(key, -RECENT_MESSAGE_LIMIT, -1);
            redis.expire(key, Duration.ofDays(7));
        } catch (Exception ignored) { }
    }

    private static String cacheKey(long userId, String id) { return "sxw:conversation:recent:" + userId + ":" + id; }
    public record DeletionPreview(long messageCount, long attachmentCount, long inferredMemoryCount) { }
    private ConversationSummary map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new ConversationSummary(rs.getString("conversation_id"), rs.getString("title"), AgentProfileCode.valueOf(rs.getString("profile_code")), rs.getBoolean("pinned"), rs.getString("rolling_summary"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
