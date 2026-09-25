package com.sxw.sxwaiagent.memory;

import com.sxw.sxwaiagent.context.TokenCounter;
import com.sxw.sxwaiagent.knowledge.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** User-scoped candidate, durable memory and semantic retrieval service. */
@Service
public class UserMemoryService {
    private static final int MAX_ALWAYS_ON_ITEMS = 5;
    private static final int MAX_RELEVANT_ITEMS = 5;
    private final JdbcTemplate jdbc;
    private final OutboxService outbox;
    private final EmbeddingService embeddings;
    private final TokenCounter tokens;

    @Value("${sxw.agent.memory.always-on-token-budget:300}") private int alwaysOnTokenBudget;
    @Value("${sxw.agent.memory.relevant-token-budget:500}") private int relevantTokenBudget;
    @Value("${sxw.agent.memory.semantic-threshold:0.45}") private double semanticThreshold;

    public UserMemoryService(JdbcTemplate jdbc, OutboxService outbox,
                             EmbeddingService embeddings, TokenCounter tokens) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.embeddings = embeddings;
        this.tokens = tokens;
    }

    public List<Map<String, Object>> list(long userId, String status) {
        return jdbc.query("""
                SELECT memory_id,name,description,memory_type,status,source_kind,always_on,
                       scope_type,scope_id,priority,created_at,deleted_at,purged_at
                  FROM ai_memory_item
                 WHERE user_id=? AND (CAST(? AS varchar) IS NULL OR status=?)
                   AND (status='DELETED' OR deleted_at IS NULL)
                 ORDER BY always_on DESC,created_at DESC LIMIT 100
                """, (rs, rowNum) -> memoryMap(rs), userId, status, status);
    }

    /** Creates one immutable retrieval result for the entire agent request/tool loop. */
    public RetrievalSnapshot snapshot(long userId, String query, String agentId,
                                      String projectId, String relationshipId) {
        List<MemoryHit> alwaysOn = fit(loadAlwaysOn(userId, agentId, projectId, relationshipId),
                MAX_ALWAYS_ON_ITEMS, alwaysOnTokenBudget);
        List<MemoryHit> relevant = query == null || query.isBlank() ? List.of()
                : fit(loadRelevant(userId, query, agentId, projectId, relationshipId),
                MAX_RELEVANT_ITEMS, relevantTokenBudget);
        return new RetrievalSnapshot(UUID.randomUUID().toString(), Instant.now(), alwaysOn, relevant,
                tokensOf(alwaysOn), tokensOf(relevant));
    }

    public String activeIndex(long userId) {
        return render(fit(loadAlwaysOn(userId, null, null, null), MAX_ALWAYS_ON_ITEMS, alwaysOnTokenBudget));
    }

    public String relevant(long userId, String query) {
        return render(fit(loadRelevant(userId, query, null, null, null), MAX_RELEVANT_ITEMS, relevantTokenBudget));
    }

    @Transactional
    public Map<String, Object> explicit(long userId, String content) {
        return explicit(userId, null, content, "GLOBAL", null);
    }

    @Transactional
    public Map<String, Object> explicit(long userId, String conversationId, String content,
                                        String scopeType, String scopeId) {
        validate(content);
        lockUser(userId);
        if (conversationId != null && !conversationId.isBlank()) {
            Integer owned = jdbc.queryForObject("SELECT count(*) FROM ai_conversation WHERE conversation_id=? AND user_id=?",
                    Integer.class, conversationId, userId);
            if (owned == null || owned == 0) throw new IllegalArgumentException("Conversation not found");
        }
        String normalizedScope = normalizeScope(scopeType);
        String contentHash = hash(normalize(content));
        ExistingMemory existing = findActiveByHash(userId, contentHash, normalizedScope, scopeId);
        if (existing != null) {
            return Map.of("memoryId", existing.memoryId(), "candidateId", "",
                    "status", "ACTIVE", "alwaysOn", existing.alwaysOn(), "duplicate", true);
        }

        String candidateId = "cand_" + id();
        String memoryId = "mem_" + id();
        jdbc.update("""
                INSERT INTO ai_memory_candidate(
                    candidate_id,user_id,conversation_id,source_conversation_id,source_kind,
                    approval_mode,memory_type,title,content,content_hash,status)
                VALUES (?,?,?,?,?,'AUTO_APPROVE','USER','User preference',?,?,'PENDING')
                """, candidateId, userId, conversationId, conversationId, "EXPLICIT", content, contentHash);
        candidateEvent(candidateId, userId, null, "PENDING", "user:" + userId, "explicit request");
        jdbc.update("UPDATE ai_memory_candidate SET status='APPROVED',reviewed_by=?,reviewed_at=NOW() "
                + "WHERE candidate_id=? AND user_id=? AND status='PENDING'", "user:" + userId, candidateId, userId);
        candidateEvent(candidateId, userId, "PENDING", "APPROVED", "user:" + userId, "auto approved");
        insertMemory(memoryId, userId, candidateId, conversationId, "EXPLICIT", "USER",
                "User preference", content, contentHash, normalizedScope, scopeId, false);
        setAlwaysOnInternal(userId, memoryId, true, false);
        jdbc.update("UPDATE ai_memory_candidate SET status='APPLIED',applied_memory_id=? "
                + "WHERE candidate_id=? AND user_id=? AND status='APPROVED'", memoryId, candidateId, userId);
        candidateEvent(candidateId, userId, "APPROVED", "APPLIED", "system", "memory applied");
        enqueueEmbedding(memoryId, content);
        return Map.of("memoryId", memoryId, "candidateId", candidateId,
                "status", "ACTIVE", "alwaysOn", true, "duplicate", false);
    }

    /** Creates an inferred PENDING candidate owned by the conversation user. */
    @Transactional
    public String inferredCandidate(String conversationId, String memoryType,
                                    String title, String content, String scopeType, String scopeId) {
        validate(content);
        Long userId = jdbc.query("SELECT user_id FROM ai_conversation WHERE conversation_id=?",
                rs -> rs.next() ? rs.getLong(1) : null, conversationId);
        if (userId == null) throw new IllegalArgumentException("Conversation not found");
        lockUser(userId);
        String normalizedScope = normalizeScope(scopeType);
        String contentHash = hash(normalize(content));
        if (findActiveByHash(userId, contentHash, normalizedScope, scopeId) != null) return "";
        List<String> pending = jdbc.query("SELECT candidate_id FROM ai_memory_candidate "
                        + "WHERE user_id=? AND content_hash=? AND status='PENDING' LIMIT 1",
                (rs, rowNum) -> rs.getString(1), userId, contentHash);
        if (!pending.isEmpty()) return pending.getFirst();

        String candidateId = "cand_" + id();
        jdbc.update("""
                INSERT INTO ai_memory_candidate(
                    candidate_id,user_id,conversation_id,source_conversation_id,source_kind,
                    approval_mode,memory_type,scope_type,scope_id,title,content,content_hash,status)
                VALUES (?,?,?,?,?,'USER_REVIEW',?,?,?,?,?,?,'PENDING')
                """, candidateId, userId, conversationId, conversationId, "INFERRED",
                memoryType == null ? "USER" : memoryType, normalizedScope, scopeId,
                title == null || title.isBlank() ? "Memory suggestion" : title, content, contentHash);
        candidateEvent(candidateId, userId, null, "PENDING", "hermes", "inferred from conversation");
        return candidateId;
    }

    public List<Map<String, Object>> candidates(long userId) {
        return jdbc.query("""
                SELECT candidate_id,title,content,status,source_kind,scope_type,scope_id,created_at
                  FROM ai_memory_candidate WHERE user_id=? AND status IN ('PENDING','APPROVED')
                 ORDER BY created_at DESC
                """, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidateId", rs.getString(1)); item.put("title", rs.getString(2));
            item.put("content", rs.getString(3)); item.put("status", rs.getString(4));
            item.put("sourceKind", rs.getString(5)); item.put("scopeType", rs.getString(6));
            item.put("scopeId", rs.getString(7)); item.put("createdAt", rs.getTimestamp(8).toInstant().toString());
            return item;
        }, userId);
    }

    @Transactional
    public void decideCandidate(long userId, String candidateId, boolean approve) {
        Candidate candidate = jdbc.query("""
                SELECT candidate_id,memory_type,title,content,content_hash,source_conversation_id,
                       scope_type,scope_id,status FROM ai_memory_candidate
                 WHERE candidate_id=? AND user_id=? FOR UPDATE
                """, rs -> rs.next() ? candidate(rs) : null, candidateId, userId);
        if (candidate == null || !"PENDING".equals(candidate.status()))
            throw new IllegalArgumentException("Candidate not found or already reviewed");
        String actor = "user:" + userId;
        if (!approve) {
            jdbc.update("UPDATE ai_memory_candidate SET status='REJECTED',reviewed_by=?,reviewed_at=NOW() "
                    + "WHERE candidate_id=? AND user_id=? AND status='PENDING'", actor, candidateId, userId);
            candidateEvent(candidateId, userId, "PENDING", "REJECTED", actor, "user rejected");
            return;
        }

        jdbc.update("UPDATE ai_memory_candidate SET status='APPROVED',reviewed_by=?,reviewed_at=NOW() "
                + "WHERE candidate_id=? AND user_id=? AND status='PENDING'", actor, candidateId, userId);
        candidateEvent(candidateId, userId, "PENDING", "APPROVED", actor, "user approved");
        ExistingMemory duplicate = findActiveByHash(userId, candidate.contentHash(), candidate.scopeType(), candidate.scopeId());
        String memoryId;
        if (duplicate != null) memoryId = duplicate.memoryId();
        else {
            memoryId = "mem_" + id();
            insertMemory(memoryId, userId, candidateId, candidate.sourceConversationId(), "INFERRED",
                    candidate.memoryType(), candidate.title(), candidate.content(), candidate.contentHash(),
                    candidate.scopeType(), candidate.scopeId(), false);
            enqueueEmbedding(memoryId, candidate.content());
        }
        jdbc.update("UPDATE ai_memory_candidate SET status='APPLIED',applied_memory_id=? "
                + "WHERE candidate_id=? AND user_id=? AND status='APPROVED'", memoryId, candidateId, userId);
        candidateEvent(candidateId, userId, "APPROVED", "APPLIED", "system", "memory applied");
    }

    @Transactional
    public void decide(long userId, String memoryId, boolean approve, boolean alwaysOn) {
        if (!approve) { archive(userId, memoryId); return; }
        restore(userId, memoryId);
        setAlwaysOn(userId, memoryId, alwaysOn);
    }

    @Transactional public void archive(long userId, String memoryId) {
        requireUpdate(jdbc.update("UPDATE ai_memory_item SET status='ARCHIVED',always_on=false,reviewed_at=NOW(),reviewed_by=? "
                + "WHERE memory_id=? AND user_id=? AND status IN ('ACTIVE','DISABLED') AND deleted_at IS NULL",
                "user:" + userId, memoryId, userId));
    }

    @Transactional public void restore(long userId, String memoryId) {
        requireUpdate(jdbc.update("UPDATE ai_memory_item SET status='ACTIVE',deleted_at=NULL,deletion_reason=NULL,"
                        + "reviewed_at=NOW(),reviewed_by=? WHERE memory_id=? AND user_id=? "
                        + "AND status IN ('ARCHIVED','DISABLED') AND purged_at IS NULL",
                "user:" + userId, memoryId, userId));
    }

    @Transactional public void delete(long userId, String memoryId, String reason) {
        requireUpdate(jdbc.update("UPDATE ai_memory_item SET status='DELETED',always_on=false,deleted_at=NOW(),"
                        + "deletion_reason=?,active_embedding_id=NULL,reviewed_at=NOW(),reviewed_by=? "
                        + "WHERE memory_id=? AND user_id=? AND status<>'DELETED'",
                safeReason(reason), "user:" + userId, memoryId, userId));
    }

    @Transactional public void purge(long userId, String memoryId, String reason) {
        requireUpdate(jdbc.update("UPDATE ai_memory_item SET status='DELETED',always_on=false,"
                        + "deleted_at=COALESCE(deleted_at,NOW()),purge_requested_at=COALESCE(purge_requested_at,NOW()),"
                        + "deletion_reason=?,active_embedding_id=NULL,reviewed_at=NOW(),reviewed_by=? "
                        + "WHERE memory_id=? AND user_id=? AND purged_at IS NULL",
                safeReason(reason), "user:" + userId, memoryId, userId));
        outbox.enqueue("MEMORY_PURGE_REQUESTED", "MEMORY", memoryId,
                "memory-purge:" + memoryId, Map.of("memoryId", memoryId, "userId", userId));
    }

    @Transactional public void setAlwaysOn(long userId, String memoryId, boolean alwaysOn) {
        lockUser(userId); setAlwaysOnInternal(userId, memoryId, alwaysOn, true);
    }

    public List<Map<String, Object>> forgetPreview(long userId, String query) {
        if (query == null || query.isBlank()) return List.of();
        String safe = query.trim();
        return jdbc.query("""
                SELECT memory_id,name,description,status,always_on FROM ai_memory_item
                 WHERE user_id=? AND status IN ('ACTIVE','ARCHIVED','DISABLED') AND deleted_at IS NULL
                   AND similarity(COALESCE(name,'') || ' ' || COALESCE(description,''), ?) >= 0.18
                 ORDER BY similarity(COALESCE(name,'') || ' ' || COALESCE(description,''), ?) DESC LIMIT 10
                """, (rs, rowNum) -> Map.of("memoryId", rs.getString(1), "name", rs.getString(2),
                "description", Objects.toString(rs.getString(3), ""), "status", rs.getString(4),
                "alwaysOn", rs.getBoolean(5)), userId, safe, safe);
    }

    public long inferredCountForConversation(long userId, String conversationId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM ai_memory_item WHERE user_id=? "
                + "AND source_conversation_id=? AND source_kind='INFERRED' AND status<>'DELETED'",
                Long.class, userId, conversationId);
        return count == null ? 0 : count;
    }

    @Transactional public int purgeInferredForConversation(long userId, String conversationId) {
        List<String> ids = jdbc.query("SELECT memory_id FROM ai_memory_item WHERE user_id=? "
                        + "AND source_conversation_id=? AND source_kind='INFERRED' AND purged_at IS NULL",
                (rs, rowNum) -> rs.getString(1), userId, conversationId);
        for (String memoryId : ids) purge(userId, memoryId, "source conversation deleted");
        return ids.size();
    }

    private List<MemoryHit> loadAlwaysOn(long userId, String agentId, String projectId, String relationshipId) {
        return jdbc.query("""
                SELECT memory_id,name,description,priority,scope_type,scope_id,1.0 AS score
                  FROM ai_memory_item WHERE user_id=? AND status='ACTIVE' AND always_on=true AND deleted_at IS NULL
                   AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP)
                   AND (scope_type='GLOBAL' OR (scope_type='AGENT' AND scope_id=?)
                        OR (CAST(? AS varchar) IS NOT NULL AND scope_type='PROJECT' AND scope_id=?)
                        OR (CAST(? AS varchar) IS NOT NULL AND scope_type='RELATIONSHIP' AND scope_id=?))
                 ORDER BY CASE priority WHEN 'BOUNDARY' THEN 0 WHEN 'USER_PINNED' THEN 1 ELSE 2 END,
                          activated_at DESC NULLS LAST LIMIT 20
                """, this::memoryHit, userId, agentId, projectId, projectId, relationshipId, relationshipId);
    }

    private List<MemoryHit> loadRelevant(long userId, String query, String agentId,
                                         String projectId, String relationshipId) {
        if (query == null || query.isBlank()) return List.of();
        try {
            float[] embedding = embeddings.embed("query: " + query);
            if (embedding.length == 1024) {
                String vector = vector(embedding);
                List<MemoryHit> semantic = jdbc.query("""
                        SELECT m.memory_id,m.name,m.description,m.priority,m.scope_type,m.scope_id,
                               1-(e.embedding <=> CAST(? AS vector)) AS score
                          FROM ai_memory_item m JOIN ai_memory_embedding e ON e.id=m.active_embedding_id
                         WHERE m.user_id=? AND m.status='ACTIVE' AND m.always_on=false AND m.deleted_at IS NULL
                           AND e.status='READY' AND (m.expired_at IS NULL OR m.expired_at>CURRENT_TIMESTAMP)
                           AND (m.scope_type='GLOBAL' OR (m.scope_type='AGENT' AND m.scope_id=?)
                                OR (CAST(? AS varchar) IS NOT NULL AND m.scope_type='PROJECT' AND m.scope_id=?)
                                OR (CAST(? AS varchar) IS NOT NULL AND m.scope_type='RELATIONSHIP' AND m.scope_id=?))
                           AND 1-(e.embedding <=> CAST(? AS vector)) >= ?
                         ORDER BY e.embedding <=> CAST(? AS vector),m.created_at DESC LIMIT 20
                        """, this::memoryHit, vector, userId, agentId, projectId, projectId,
                        relationshipId, relationshipId, vector, semanticThreshold, vector);
                if (!semantic.isEmpty()) return semantic;
            }
        } catch (Exception ignored) { }
        return keywordFallback(userId, query, agentId, projectId, relationshipId);
    }

    private List<MemoryHit> keywordFallback(long userId, String query, String agentId,
                                            String projectId, String relationshipId) {
        return jdbc.query("""
                SELECT memory_id,name,description,priority,scope_type,scope_id,
                       similarity(COALESCE(name,'') || ' ' || COALESCE(description,''), ?) AS score
                  FROM ai_memory_item WHERE user_id=? AND status='ACTIVE' AND always_on=false AND deleted_at IS NULL
                   AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP)
                   AND (scope_type='GLOBAL' OR (scope_type='AGENT' AND scope_id=?)
                        OR (CAST(? AS varchar) IS NOT NULL AND scope_type='PROJECT' AND scope_id=?)
                        OR (CAST(? AS varchar) IS NOT NULL AND scope_type='RELATIONSHIP' AND scope_id=?))
                   AND similarity(COALESCE(name,'') || ' ' || COALESCE(description,''), ?) >= 0.18
                 ORDER BY score DESC,created_at DESC LIMIT 20
                """, this::memoryHit, query, userId, agentId, projectId, projectId,
                relationshipId, relationshipId, query);
    }

    List<MemoryHit> fit(List<MemoryHit> input, int maxItems, int tokenBudget) {
        List<MemoryHit> result = new ArrayList<>(); int remaining = Math.max(0, tokenBudget);
        for (MemoryHit hit : input) {
            if (result.size() >= maxItems || remaining <= 0) break;
            String line = line(hit.name(), hit.description()); int needed = tokens.count(line);
            if (needed <= remaining) { result.add(hit.withTokens(needed)); remaining -= needed; }
            else if (result.isEmpty()) {
                int prefixTokens = tokens.count("- " + hit.name() + ": ");
                if (prefixTokens >= remaining) break;
                String compactText = truncateToTokenBudget(hit.description(), remaining - prefixTokens);
                MemoryHit compact = hit.withDescription(compactText);
                int compactTokens = tokens.count(line(compact.name(), compact.description()));
                if (compactTokens <= remaining) result.add(compact.withTokens(compactTokens));
                remaining = 0;
            }
        }
        return List.copyOf(result);
    }

    private String truncateToTokenBudget(String value, int budget) {
        if (value == null || value.isBlank() || budget <= 0) return "";
        if (tokens.count(value) <= budget) return value;
        int low = 0, high = value.length(), best = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            String candidate = value.substring(0, middle).stripTrailing() + "…";
            if (tokens.count(candidate) <= budget) { best = middle; low = middle + 1; }
            else high = middle - 1;
        }
        return best == 0 ? "" : value.substring(0, best).stripTrailing() + "…";
    }

    private void insertMemory(String memoryId, long userId, String candidateId, String conversationId,
                              String sourceKind, String memoryType, String title, String content,
                              String contentHash, String scopeType, String scopeId, boolean alwaysOn) {
        jdbc.update("""
                INSERT INTO ai_memory_item(memory_id,memory_type,name,description,rule_text,why_text,apply_text,
                    confidence,status,created_at,user_id,source_kind,always_on,activated_at,source_candidate_id,
                    source_conversation_id,scope_type,scope_id,priority,content_hash)
                VALUES (?,?,?,?,?,?,?,?, 'ACTIVE',NOW(),?,?,?,?,?,?,?,?,?,?)
                """, memoryId, memoryType, title, content, content, "Approved memory", content,
                "EXPLICIT".equals(sourceKind) ? BigDecimal.ONE : new BigDecimal("0.8"), userId, sourceKind,
                alwaysOn, alwaysOn ? java.sql.Timestamp.from(Instant.now()) : null, candidateId,
                conversationId, scopeType, scopeId, "NORMAL", contentHash);
    }

    private void setAlwaysOnInternal(long userId, String memoryId, boolean alwaysOn, boolean userPinned) {
        String priority = userPinned && alwaysOn ? "USER_PINNED" : null;
        requireUpdate(jdbc.update("UPDATE ai_memory_item SET always_on=?,"
                        + "activated_at=CASE WHEN ? THEN NOW() ELSE activated_at END,priority=COALESCE(?,priority) "
                        + "WHERE memory_id=? AND user_id=? AND status='ACTIVE' AND deleted_at IS NULL",
                alwaysOn, alwaysOn, priority, memoryId, userId));
        if (!alwaysOn) return;
        while (alwaysOnOverCapacity(userId)) {
            List<String> demotable = jdbc.query("SELECT memory_id FROM ai_memory_item WHERE user_id=? "
                            + "AND status='ACTIVE' AND always_on=true AND deleted_at IS NULL AND memory_id<>? "
                            + "AND priority NOT IN ('BOUNDARY','USER_PINNED') "
                            + "ORDER BY CASE priority WHEN 'NORMAL' THEN 0 ELSE 1 END,activated_at ASC NULLS FIRST LIMIT 1",
                    (rs, rowNum) -> rs.getString(1), userId, memoryId);
            if (demotable.isEmpty())
                throw new IllegalStateException("No replaceable always-on memory; choose one to unpin first");
            jdbc.update("UPDATE ai_memory_item SET always_on=false WHERE memory_id=? AND user_id=?",
                    demotable.getFirst(), userId);
        }
    }

    private boolean alwaysOnOverCapacity(long userId) {
        List<String> lines = jdbc.query("SELECT name,description FROM ai_memory_item WHERE user_id=? "
                        + "AND status='ACTIVE' AND always_on=true AND deleted_at IS NULL",
                (rs, rowNum) -> line(rs.getString(1), rs.getString(2)), userId);
        return lines.size() > MAX_ALWAYS_ON_ITEMS || tokens.count(String.join("\n", lines)) > alwaysOnTokenBudget;
    }

    private ExistingMemory findActiveByHash(long userId, String contentHash, String scopeType, String scopeId) {
        return jdbc.query("SELECT memory_id,always_on FROM ai_memory_item WHERE user_id=? AND content_hash=? "
                        + "AND scope_type=? AND COALESCE(scope_id,'')=COALESCE(?,'') "
                        + "AND status='ACTIVE' AND deleted_at IS NULL LIMIT 1",
                rs -> rs.next() ? new ExistingMemory(rs.getString(1), rs.getBoolean(2)) : null,
                userId, contentHash, scopeType, scopeId);
    }

    private void lockUser(long userId) {
        List<Long> owner = jdbc.query("SELECT id FROM sxw_users WHERE id=? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), userId);
        if (owner.isEmpty()) throw new IllegalArgumentException("User not found");
    }

    private void enqueueEmbedding(String memoryId, String content) {
        outbox.enqueue("MEMORY_EMBEDDING_REQUESTED", "MEMORY", memoryId,
                "memory-embedding:" + memoryId, Map.of("memoryId", memoryId, "content", content));
    }

    private void candidateEvent(String candidateId, long userId, String from, String to,
                                String actor, String reason) {
        jdbc.update("INSERT INTO ai_memory_candidate_event(candidate_id,user_id,from_status,to_status,actor,reason) "
                + "VALUES (?,?,?,?,?,?)", candidateId, userId, from, to, actor, reason);
    }

    private MemoryHit memoryHit(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryHit(rs.getString(1), rs.getString(2), Objects.toString(rs.getString(3), ""),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getDouble(7), 0);
    }

    private Candidate candidate(ResultSet rs) throws SQLException {
        return new Candidate(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9));
    }

    private Map<String, Object> memoryMap(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("memoryId", rs.getString("memory_id")); item.put("name", rs.getString("name"));
        item.put("description", rs.getString("description")); item.put("memoryType", rs.getString("memory_type"));
        item.put("status", rs.getString("status")); item.put("sourceKind", rs.getString("source_kind"));
        item.put("alwaysOn", rs.getBoolean("always_on")); item.put("scopeType", rs.getString("scope_type"));
        item.put("scopeId", rs.getString("scope_id")); item.put("priority", rs.getString("priority"));
        item.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        item.put("deletedAt", rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant().toString());
        item.put("purgedAt", rs.getTimestamp("purged_at") == null ? null : rs.getTimestamp("purged_at").toInstant().toString());
        return item;
    }

    private static String render(List<MemoryHit> hits) {
        return hits.stream().map(hit -> line(hit.name(), hit.description()))
                .reduce("", (left, right) -> left.isEmpty() ? right : left + '\n' + right);
    }
    private static int tokensOf(List<MemoryHit> hits) { return hits.stream().mapToInt(MemoryHit::tokens).sum(); }
    private static String line(String name, String description) {
        return "- " + Objects.toString(name, "Memory") + ": " + Objects.toString(description, "");
    }
    private static String normalizeScope(String scopeType) {
        String value = scopeType == null ? "GLOBAL" : scopeType.toUpperCase(Locale.ROOT);
        if (!List.of("GLOBAL", "AGENT", "PROJECT", "RELATIONSHIP").contains(value))
            throw new IllegalArgumentException("Unsupported memory scope");
        return value;
    }
    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "user request";
        return reason.length() > 255 ? reason.substring(0, 255) : reason;
    }
    private static void requireUpdate(int count) {
        if (count == 0) throw new IllegalArgumentException("Memory not found or state transition not allowed");
    }
    private static void validate(String content) {
        if (content == null || content.isBlank() || content.length() > 500)
            throw new IllegalArgumentException("Invalid memory content");
        if (content.matches("(?is).*(?:password|secret|api[_ -]?key|token|密码|密钥)\\s*(?:是|为|[:=])\\s*\\S{4,}.*")
                || content.matches("(?is).*(?:bearer\\s+[a-z0-9._-]{12,}|sk-[a-z0-9_-]{12,}).*"))
            throw new IllegalArgumentException("Sensitive information cannot be stored as memory");
    }
    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Unable to hash memory", e); }
    }
    private static String vector(float[] values) {
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) output.append(','); output.append(values[index]);
        }
        return output.append(']').toString();
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    public record RetrievalSnapshot(String snapshotId, Instant createdAt,
                                    List<MemoryHit> alwaysOn, List<MemoryHit> relevant,
                                    int alwaysOnTokens, int relevantTokens) {
        public String alwaysOnText() { return render(alwaysOn); }
        public String relevantText() { return render(relevant); }
        public List<String> alwaysOnIds() { return alwaysOn.stream().map(MemoryHit::memoryId).toList(); }
        public List<String> relevantIds() { return relevant.stream().map(MemoryHit::memoryId).toList(); }
    }
    public record MemoryHit(String memoryId, String name, String description, String priority,
                            String scopeType, String scopeId, double score, int tokens) {
        MemoryHit withTokens(int value) { return new MemoryHit(memoryId,name,description,priority,scopeType,scopeId,score,value); }
        MemoryHit withDescription(String value) { return new MemoryHit(memoryId,name,value,priority,scopeType,scopeId,score,tokens); }
    }
    private record Candidate(String candidateId,String memoryType,String title,String content,String contentHash,
                             String sourceConversationId,String scopeType,String scopeId,String status) { }
    private record ExistingMemory(String memoryId, boolean alwaysOn) { }
}
