package com.sxw.sxwaiagent.agent.prompt;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.context.ContextAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persists immutable prompt identity and metadata for every actual model call. */
@Component
public class PromptRunRecorder {
    private static final Logger log = LoggerFactory.getLogger(PromptRunRecorder.class);

    private final JdbcTemplate jdbc;
    private final PromptVersionCatalog catalog;
    private final ContextAuditService contextAudit;
    private final Map<String, Boolean> versionStates = new ConcurrentHashMap<>();

    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String defaultModel = "qwen-plus";

    public PromptRunRecorder(JdbcTemplate jdbc, PromptVersionCatalog catalog,
                             ContextAuditService contextAudit) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.contextAudit = contextAudit;
    }

    public void record(AgentContext context, int callNo, AssembledPrompt assembledPrompt,
                       String effectiveSystem, List<Message> actualMessages,
                       int inputTokens, Map<String, Integer> grants) {
        PromptIdentity identity = catalog.identify(context.profile(), assembledPrompt);
        try {
            String versionKey = identity.code() + ":" + identity.version() + ":" + identity.templateHash();
            boolean drift = versionStates.computeIfAbsent(versionKey,
                    ignored -> registerVersion(identity, assembledPrompt.sections()));
            Long userId = context.metadata().get("userId") instanceof Number n ? n.longValue() : null;
            String model = stringMetadata(context, "model");
            if (model == null) model = defaultModel;
            jdbc.update("""
                    INSERT INTO ai_prompt_run(
                        request_id,trace_id,conversation_id,user_id,call_no,prompt_code,prompt_version,
                        model,template_hash,static_hash,dynamic_hash,rendered_hash,rendered_length,
                        input_tokens,section_count,status,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())
                    ON CONFLICT (request_id,call_no) WHERE call_no IS NOT NULL DO UPDATE SET
                        trace_id=EXCLUDED.trace_id, conversation_id=EXCLUDED.conversation_id,
                        user_id=EXCLUDED.user_id, model=EXCLUDED.model,
                        template_hash=EXCLUDED.template_hash, static_hash=EXCLUDED.static_hash,
                        dynamic_hash=EXCLUDED.dynamic_hash, rendered_hash=EXCLUDED.rendered_hash,
                        rendered_length=EXCLUDED.rendered_length, input_tokens=EXCLUDED.input_tokens,
                        section_count=EXCLUDED.section_count, status=EXCLUDED.status
                    """, context.requestId(), context.traceId(), context.chatId(), userId, callNo,
                    identity.code(), identity.version(), model, identity.templateHash(),
                    assembledPrompt.staticHash(), assembledPrompt.dynamicHash(), assembledPrompt.renderedHash(),
                    effectiveSystem == null ? 0 : effectiveSystem.length(), inputTokens,
                    assembledPrompt.sections().size(), drift ? "VERSION_DRIFT" : "PREPARED");
            contextAudit.record(context, callNo, assembledPrompt, effectiveSystem, actualMessages);
            log.debug("Recorded prompt call requestId={}, callNo={}, code={}, version={}, tokens={}, grants={}",
                    context.requestId(), callNo, identity.code(), identity.version(), inputTokens, grants);
        } catch (Exception e) {
            // Observability must never make a successful user request fail.
            log.error("Failed to record prompt call requestId={}, callNo={}: {}",
                    context.requestId(), callNo, e.getMessage());
        }
    }

    public void markCall(String requestId, int callNo, String status) {
        try {
            jdbc.update("""
                    UPDATE ai_prompt_run
                       SET status=CASE WHEN status='VERSION_DRIFT' AND ?='COMPLETED'
                                       THEN 'COMPLETED_WITH_DRIFT' ELSE ? END
                     WHERE request_id=? AND call_no=?
                    """, status, status, requestId, callNo);
        } catch (Exception e) {
            log.warn("Unable to update prompt call status requestId={}, callNo={}: {}",
                    requestId, callNo, e.getMessage());
        }
    }

    /** Compatibility entry point for legacy/offline callers. */
    public void record(String requestId, String promptCode, int promptVersion,
                       AssembledPrompt assembledPrompt) {
        try {
            jdbc.update("""
                    INSERT INTO ai_prompt_run(request_id,prompt_code,prompt_version,static_hash,dynamic_hash,
                                              rendered_hash,rendered_length,section_count,status,created_at)
                    VALUES (?,?,?,?,?,?,?,?, 'LEGACY',NOW())
                    """, requestId, promptCode, promptVersion, assembledPrompt.staticHash(),
                    assembledPrompt.dynamicHash(), assembledPrompt.renderedHash(),
                    assembledPrompt.renderedLength(), assembledPrompt.sections().size());
        } catch (Exception e) {
            log.error("Failed to record legacy prompt run requestId={}: {}", requestId, e.getMessage());
        }
    }

    private boolean registerVersion(PromptIdentity identity, List<PromptSection> sections) {
        boolean drift = false;
        for (PromptSection section : sections) {
            if (!section.isStatic()) continue;
            String content = section.content() == null ? "" : section.content();
            String hash = ContextAuditService.sha256(content);
            jdbc.update("""
                    INSERT INTO ai_prompt_version(prompt_code,version,section_key,content,description,
                                                  content_hash,template_hash,status)
                    VALUES (?,?,?,?,?,?,?,'ACTIVE') ON CONFLICT (prompt_code,version,section_key) DO NOTHING
                    """, identity.code(), identity.version(), section.key(), content,
                    "Trusted static prompt section", hash, identity.templateHash());
            jdbc.update("""
                    UPDATE ai_prompt_version SET content_hash=?,template_hash=?
                     WHERE prompt_code=? AND version=? AND section_key=? AND content=? AND content_hash IS NULL
                    """, hash, identity.templateHash(), identity.code(), identity.version(),
                    section.key(), content);
            String storedHash = jdbc.query("""
                    SELECT content_hash FROM ai_prompt_version
                     WHERE prompt_code=? AND version=? AND section_key=?
                    """, rs -> rs.next() ? rs.getString(1) : null,
                    identity.code(), identity.version(), section.key());
            if (!hash.equals(storedHash)) {
                drift = true;
                log.error("Prompt version drift detected: code={}, version={}, section={}; bump prompt version",
                        identity.code(), identity.version(), section.key());
            }
        }
        return drift;
    }

    private static String stringMetadata(AgentContext context, String key) {
        Object value = context.metadata().get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
