package com.sxw.sxwaiagent.agent.prompt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only, owner-scoped views of prompt and context audit metadata. */
@Service
public class PromptObservabilityService {
    private final JdbcTemplate jdbc;

    public PromptObservabilityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PromptRunView> list(long userId, String conversationId, int limit) {
        return jdbc.query("""
                SELECT id,request_id,trace_id,call_no,prompt_code,prompt_version,model,template_hash,
                       static_hash,dynamic_hash,rendered_hash,rendered_length,input_tokens,
                       section_count,status,created_at
                  FROM ai_prompt_run
                 WHERE user_id=? AND conversation_id=?
                 ORDER BY created_at DESC,id DESC LIMIT ?
                """, (rs, row) -> new PromptRunView(rs.getLong("id"), rs.getString("request_id"),
                rs.getString("trace_id"), rs.getInt("call_no"), rs.getString("prompt_code"),
                rs.getInt("prompt_version"), rs.getString("model"), rs.getString("template_hash"),
                rs.getString("static_hash"), rs.getString("dynamic_hash"), rs.getString("rendered_hash"),
                rs.getInt("rendered_length"), rs.getInt("input_tokens"), rs.getInt("section_count"),
                rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()),
                userId, conversationId, Math.min(Math.max(limit, 1), 100));
    }

    public PromptCallDetail detail(long userId, String conversationId, String requestId, int callNo) {
        List<PromptRunView> runs = jdbc.query("""
                SELECT id,request_id,trace_id,call_no,prompt_code,prompt_version,model,template_hash,
                       static_hash,dynamic_hash,rendered_hash,rendered_length,input_tokens,
                       section_count,status,created_at
                  FROM ai_prompt_run
                 WHERE user_id=? AND conversation_id=? AND request_id=? AND call_no=?
                """, (rs, row) -> new PromptRunView(rs.getLong("id"), rs.getString("request_id"),
                rs.getString("trace_id"), rs.getInt("call_no"), rs.getString("prompt_code"),
                rs.getInt("prompt_version"), rs.getString("model"), rs.getString("template_hash"),
                rs.getString("static_hash"), rs.getString("dynamic_hash"), rs.getString("rendered_hash"),
                rs.getInt("rendered_length"), rs.getInt("input_tokens"), rs.getInt("section_count"),
                rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()),
                userId, conversationId, requestId, callNo);
        if (runs.isEmpty()) throw new IllegalArgumentException("Prompt call not found");
        List<ContextItemView> items = jdbc.query("""
                SELECT section_key,section_kind,item_type,item_id,inclusion_status,content_hash,
                       content_length,token_count,content_preview,source_ref
                  FROM ai_context_item
                 WHERE user_id=? AND conversation_id=? AND request_id=? AND call_no=?
                 ORDER BY id
                """, (rs, row) -> new ContextItemView(rs.getString("section_key"),
                rs.getString("section_kind"), rs.getString("item_type"), rs.getString("item_id"),
                rs.getString("inclusion_status"), rs.getString("content_hash"),
                rs.getInt("content_length"), rs.getInt("token_count"),
                rs.getString("content_preview"), rs.getString("source_ref")),
                userId, conversationId, requestId, callNo);
        return new PromptCallDetail(runs.getFirst(), items);
    }

    public record PromptRunView(long id, String requestId, String traceId, int callNo,
                                String promptCode, int promptVersion, String model, String templateHash,
                                String staticHash, String dynamicHash, String renderedHash,
                                int renderedLength, int inputTokens, int sectionCount,
                                String status, LocalDateTime createdAt) { }

    public record ContextItemView(String sectionKey, String sectionKind, String itemType, String itemId,
                                  String inclusionStatus, String contentHash, int contentLength,
                                  int tokenCount, String contentPreview, String sourceRef) { }

    public record PromptCallDetail(PromptRunView run, List<ContextItemView> contextItems) { }
}
