package com.sxw.sxwaiagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.context.TokenCounter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Stores the exact model-visible input needed for equivalent-input replay. */
@Service
public class AgentRunSnapshotService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TokenCounter tokens;

    public AgentRunSnapshotService(JdbcTemplate jdbc, ObjectMapper json, TokenCounter tokens) {
        this.jdbc = jdbc;
        this.json = json;
        this.tokens = tokens;
    }

    public String begin(String conversationId, long userId, String requestId, String traceId) {
        String proposed = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        return jdbc.queryForObject("""
                INSERT INTO ai_agent_run(run_id,conversation_id,user_id,request_id,trace_id,status)
                VALUES (?,?,?,?,?,'RUNNING')
                ON CONFLICT (conversation_id,request_id)
                DO UPDATE SET trace_id=COALESCE(ai_agent_run.trace_id,EXCLUDED.trace_id)
                RETURNING run_id
                """, String.class, proposed, conversationId, userId, requestId, traceId);
    }

    public void capture(String requestId, int callNo, AgentContext context, AssembledPrompt assembled,
                        List<Message> actualMessages, int inputTokens, Map<String, Integer> grants) {
        String runId = jdbc.query("SELECT run_id FROM ai_agent_run WHERE request_id=? AND conversation_id=? "
                        + "ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, requestId, context.chatId());
        if (runId == null) return; // offline/unit harnesses do not always create a run row
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (Message message : actualMessages) {
                messages.add(Map.of("type", message.getMessageType().name(), "text", message.getText()));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requestId", requestId);
            body.put("traceId", context.traceId());
            body.put("profile", context.profile().code().name());
            body.put("runMode", context.runMode() == null ? "CHAT" : context.runMode().name());
            body.put("planId", context.planId());
            body.put("messages", messages);
            body.put("assembledPromptHash", assembled.renderedHash());
            body.put("staticPromptHash", assembled.staticHash());
            body.put("dynamicPromptHash", assembled.dynamicHash());
            body.put("budgetGrants", grants);
            body.put("contextVersions", Map.of(
                    "summary", number(context.metadata().get("summaryVersion")),
                    "workingMemory", number(context.metadata().get("workingMemoryVersion")),
                    "recentStart", number(context.metadata().get("recentStartSequence")),
                    "recentEnd", number(context.metadata().get("recentEndSequence"))));
            List<String> effectiveTools = effectiveTools(context);
            body.put("enabledTools", effectiveTools);
            body.put("knowledgeDocumentIds", context.metadata().getOrDefault("knowledgeDocumentIds", List.of()));
            String rendered = canonical(body);
            String contextHash = hash(rendered);
            String toolSchemaVersion = hash(canonical(effectiveTools));
            jdbc.update("""
                    INSERT INTO ai_context_snapshot(
                        run_id,call_no,summary_version_no,working_memory_version_no,
                        recent_start_sequence,recent_end_sequence,always_on_memory_ids,
                        relevant_memory_ids,tool_schema_version,system_prompt_version,
                        input_tokens,context_hash,context_json)
                    VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?::jsonb)
                    ON CONFLICT (run_id,call_no) DO UPDATE SET
                        input_tokens=EXCLUDED.input_tokens,
                        context_hash=EXCLUDED.context_hash,
                        context_json=EXCLUDED.context_json
                    """, runId, callNo,
                    nullableInt(context.metadata().get("summaryVersion")),
                    nullableInt(context.metadata().get("workingMemoryVersion")),
                    nullableLong(context.metadata().get("recentStartSequence")),
                    nullableLong(context.metadata().get("recentEndSequence")),
                    json.writeValueAsString(context.metadata().getOrDefault("alwaysOnMemoryIds", List.of())),
                    json.writeValueAsString(context.metadata().getOrDefault("relevantMemoryIds", List.of())),
                    toolSchemaVersion, context.profile().code().name() + ":" + assembled.staticHash(),
                    inputTokens > 0 ? inputTokens : tokens.count(rendered), contextHash, rendered);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist replayable context snapshot", e);
        }
    }

    public void complete(String runId, String status) { complete(runId, status, null); }

    public void complete(String runId, String status, String error) {
        jdbc.update("UPDATE ai_agent_run SET status=?,error_message=?,finished_at=NOW() WHERE run_id=?",
                status, error, runId);
    }

    public void completeByRequest(String requestId, String status, String error) {
        jdbc.update("UPDATE ai_agent_run SET status=?,error_message=?,finished_at=NOW() WHERE request_id=?",
                status, error, requestId);
    }

    public ReplayInput replayInputByTraceId(String traceId) {
        List<ReplayInput> rows = jdbc.query("""
                SELECT r.run_id,s.call_no,s.context_hash,s.context_json::text
                  FROM ai_agent_run r JOIN ai_context_snapshot s ON s.run_id=r.run_id
                 WHERE r.trace_id=? ORDER BY s.call_no
                """, (rs, rowNum) -> {
            String raw = rs.getString(4);
            try {
                Map<String, Object> context = json.readValue(raw, new TypeReference<>() { });
                return new ReplayInput(rs.getString(1), rs.getInt(2), rs.getString(3), raw,
                        hash(canonical(context)).equals(rs.getString(3)), context);
            } catch (Exception e) {
                throw new IllegalStateException("Stored context snapshot is unreadable", e);
            }
        }, traceId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Replay snapshot not found");
        return rows.getLast();
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static List<String> effectiveTools(AgentContext context) {
        LinkedHashSet<String> tools = new LinkedHashSet<>(context.profile().enabledToolNames());
        Object selected = context.metadata().get("enabledTools");
        if (selected instanceof Collection<?> collection && !collection.isEmpty()) {
            tools.retainAll(collection.stream().map(String::valueOf).toList());
        }
        if (!Boolean.TRUE.equals(context.metadata().get("webSearchEnabled"))) {
            tools.remove("searchWeb");
            tools.remove("scrapeWebPage");
        }
        return List.copyOf(tools);
    }
    private static Integer nullableInt(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private static Long nullableLong(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("sha256:");
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash context", e);
        }
    }

    private String canonical(Object value) {
        try {
            return json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Context cannot be canonicalized", e);
        }
    }

    public record ReplayInput(String runId, int callNo, String contextHash, String rawContext,
                              boolean hashVerified, Map<String, Object> context) { }
}
