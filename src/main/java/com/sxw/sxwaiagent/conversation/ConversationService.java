package com.sxw.sxwaiagent.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
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

    public ConversationService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
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
        return jdbc.query("SELECT * FROM (SELECT * FROM ai_conversation_message WHERE conversation_id=? ORDER BY id DESC LIMIT ?) m ORDER BY id",
                (rs, n) -> new ConversationMessage(rs.getLong("id"), rs.getString("role"), rs.getString("content"), rs.getTimestamp("created_at").toInstant()),
                id, Math.min(Math.max(limit, 1), 200));
    }

    @Transactional
    public void append(long userId, String id, String role, String content) {
        get(userId, id);
        jdbc.update("INSERT INTO ai_conversation_message(conversation_id,role,content) VALUES (?,?,?)", id, role, content);
        jdbc.update("UPDATE ai_conversation SET updated_at=NOW(), title=CASE WHEN title='New conversation' AND ?='USER' THEN LEFT(?,160) ELSE title END WHERE conversation_id=?",
                role, content.replaceAll("\\s+", " "), id);
        cache(userId, id, role, content, 0, Instant.now());
        refreshSummary(userId, id);
    }

    @Transactional
    public void appendAssistant(String id, String content) {
        Long userId = jdbc.query("SELECT user_id FROM ai_conversation WHERE conversation_id=?", rs -> rs.next() ? rs.getLong(1) : null, id);
        if (userId == null) return;
        jdbc.update("INSERT INTO ai_conversation_message(conversation_id,role,content) VALUES (?,?,?)", id, "ASSISTANT", content);
        jdbc.update("UPDATE ai_conversation SET updated_at=NOW() WHERE conversation_id=?", id);
        cache(userId, id, "ASSISTANT", content, 0, Instant.now());
        refreshSummary(userId, id);
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

    public void delete(long userId, String id) {
        if (jdbc.update("DELETE FROM ai_conversation WHERE conversation_id=? AND user_id=?", id, userId) == 0) throw new IllegalArgumentException("Conversation not found");
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

    private void refreshSummary(long userId, String id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ai_conversation_message WHERE conversation_id=?", Integer.class, id);
        if (count == null || count <= RECENT_MESSAGE_LIMIT || count % 10 != 0) return;
        List<ConversationMessage> prior = jdbc.query("SELECT * FROM (SELECT * FROM ai_conversation_message WHERE conversation_id=? ORDER BY id DESC OFFSET 20) x ORDER BY id DESC LIMIT 8",
                (rs, n) -> new ConversationMessage(rs.getLong("id"), rs.getString("role"), rs.getString("content"), rs.getTimestamp("created_at").toInstant()), id);
        String summary = prior.stream().map(m -> m.role() + ": " + m.content().replaceAll("\\s+", " ")).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        jdbc.update("UPDATE ai_conversation SET rolling_summary=?,summary_message_count=? WHERE conversation_id=? AND user_id=?",
                summary.length() > 3000 ? summary.substring(0, 3000) : summary, count - RECENT_MESSAGE_LIMIT, id, userId);
    }

    private static String cacheKey(long userId, String id) { return "sxw:conversation:recent:" + userId + ":" + id; }
    private ConversationSummary map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new ConversationSummary(rs.getString("conversation_id"), rs.getString("title"), AgentProfileCode.valueOf(rs.getString("profile_code")), rs.getBoolean("pinned"), rs.getString("rolling_summary"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
