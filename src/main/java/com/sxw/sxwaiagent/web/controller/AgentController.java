package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentRequest;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.orchestrator.AgentOrchestrator;
import com.sxw.sxwaiagent.agent.orchestrator.RequestGuard;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.ToolUseLoopRuntime;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.conversation.ConversationService;
import com.sxw.sxwaiagent.agent.tool.ToolRegistry;
import com.sxw.sxwaiagent.attachment.AttachmentService;
import com.sxw.sxwaiagent.conversation.ConversationEventService;
import com.sxw.sxwaiagent.conversation.ConversationContextService;
import com.sxw.sxwaiagent.memory.AgentRunSnapshotService;
import com.sxw.sxwaiagent.plan.AgentRunMode;
import com.sxw.sxwaiagent.security.PromptInjectionGuard;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalService;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一 Agent 控制器
 * <p>
 * 提供统一的 Agent 入口，支持通过 profile 参数选择不同的 Agent 模式。
 * 同时保留向后兼容的旧接口。
 */
@Tag(name = "Agent 对话", description = "统一 Agent 入口，支持多 Profile、流式响应和对话管理")
@RestController
@RequestMapping("/api/agent")
@Validated
@Slf4j
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;
    private final ToolUseLoopRuntime toolUseLoopRuntime;
    private final RequestGuard requestGuard;
    private final List<AgentProfile> agentProfiles;
    private final ConversationService conversationService;
    private final ToolRegistry toolRegistry;
    private final AttachmentService attachmentService;
    private final ConversationEventService conversationEvents;
    private final PromptInjectionGuard promptInjectionGuard;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ConversationContextService conversationContext;
    private final AgentRunSnapshotService snapshots;

    @Value("${search-api.api-key:}")
    private String searchApiKey;
    
    public AgentController(
            AgentOrchestrator agentOrchestrator,
            ToolUseLoopRuntime toolUseLoopRuntime,
            RequestGuard requestGuard,
            List<AgentProfile> agentProfiles,
            ConversationService conversationService,
            ToolRegistry toolRegistry,
            AttachmentService attachmentService,
            ConversationEventService conversationEvents,
            PromptInjectionGuard promptInjectionGuard,
            KnowledgeRetrievalService knowledgeRetrievalService,
            ConversationContextService conversationContext,
            AgentRunSnapshotService snapshots
    ) {
        this.agentOrchestrator = agentOrchestrator;
        this.toolUseLoopRuntime = toolUseLoopRuntime;
        this.requestGuard = requestGuard;
        this.agentProfiles = agentProfiles;
        this.conversationService = conversationService;
        this.toolRegistry = toolRegistry;
        this.attachmentService = attachmentService;
        this.conversationEvents = conversationEvents;
        this.promptInjectionGuard = promptInjectionGuard;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.conversationContext = conversationContext;
        this.snapshots = snapshots;
    }
    
    /**
     * 统一对话入口
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @param profile Profile 编码（LOVE / GENERAL / HERMES）
     * @return Agent 响应
     */
    @GetMapping("/chat")
    public Result<AgentResponse> chat(Authentication authentication,
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64) String chatId,
            @NotNull AgentProfileCode profile
    ) {
        long userId = currentUser(authentication);
        conversationService.ensure(userId, chatId, profile);
        String requestId = requestGuard.generateRequestId();
        var safety = promptInjectionGuard.inspect(userId, chatId, requestId, message, "USER_MESSAGE");
        if (safety.blocked()) return Result.error(422, safety.userMessage());
        String safeMessage = safety.sanitized() ? promptInjectionGuard.sanitize(message) : message;
        var start = conversationEvents.startTurn(userId, chatId, requestId, safeMessage, Map.of());
        if (!start.started()) return turnConflict(start, requestId);
        AgentRequest request = AgentRequest.builder()
                .chatId(chatId)
                .profile(profile)
                .message(safeMessage)
                .stream(false)
                .metadata(Map.of("userId", userId, "requestId", requestId,
                        "memoryReadEnabled", true, "memoryWriteEnabled", true, "promptSafety", safety))
                .build();
        
        AgentResponse response = agentOrchestrator.handleRequest(request);
        return Result.ok(response);
    }
    
    /**
     * 统一对话入口（POST）
     */
    @PostMapping("/chat")
    public Result<AgentResponse> chatPost(Authentication authentication, @RequestBody @Validated AgentChatBody body) {
        conversationService.ensure(currentUser(authentication), body.chatId(), body.profile());
        String requestId = body.requestId() == null || body.requestId().isBlank() ? requestGuard.generateRequestId() : body.requestId();
        var safety = promptInjectionGuard.inspect(currentUser(authentication), body.chatId(), requestId, body.message(), "USER_MESSAGE");
        if (safety.blocked()) return Result.error(422, safety.userMessage());
        String safeMessage = safety.sanitized() ? promptInjectionGuard.sanitize(body.message()) : body.message();
        String attachmentText = attachmentService.contextText(currentUser(authentication), body.chatId(), body.attachmentIds());
        KnowledgeRetrievalResult knowledgeResult = knowledge(body.chatId(), currentUser(authentication),
                body.knowledgeDocumentIds(), safeMessage);
        var start = conversationEvents.startTurn(currentUser(authentication), body.chatId(), requestId,
                safeMessage, Map.of("profile", body.profile().name()));
        if (!start.started()) return turnConflict(start, requestId);
        Map<String, Object> metadata = new HashMap<>(body.metadata() == null ? Map.of() : body.metadata());
        metadata.put("requestId", requestId);
        metadata.put("runMode", body.mode() == null ? AgentRunMode.CHAT.name() : body.mode().name());
        metadata.put("planId", body.planId());
        metadata.put("enabledTools", body.enabledTools() == null ? List.of() : body.enabledTools());
        metadata.put("webSearchEnabled", Boolean.TRUE.equals(body.webSearchEnabled()));
        metadata.put("attachmentIds", body.attachmentIds() == null ? List.of() : body.attachmentIds());
        metadata.put("knowledgeDocumentIds", body.knowledgeDocumentIds() == null ? List.of() : body.knowledgeDocumentIds());
        metadata.put("userId", currentUser(authentication));
        metadata.put("attachmentText", attachmentText);
        metadata.put("memoryReadEnabled", !Boolean.FALSE.equals(body.memoryReadEnabled()));
        metadata.put("memoryWriteEnabled", !Boolean.FALSE.equals(body.memoryWriteEnabled()));
        metadata.put("promptSafety", safety);
        metadata.put("knowledgeResult", knowledgeResult);
        AgentRequest request = AgentRequest.builder()
                .chatId(body.chatId())
                .profile(body.profile())
                .message(safeMessage)
                .stream(false)
                .metadata(metadata)
                .build();
        
        AgentResponse response = agentOrchestrator.handleRequest(request);
        return Result.ok(response);
    }
    
    /**
     * SSE 流式对话入口
     * <p>
     * 使用 Server-Sent Events 实时推送 AI 生成的 token。
     * 支持通过 query parameter 传递 JWT token（EventSource 不支持自定义 Header）。
     * <p>
     * SSE 事件类型：
     * - token: 文本 token ({type, content})
     * - tool_call: 工具调用通知 ({type, toolName, arguments})
     * - tool_result: 工具执行结果 ({type, toolName, result})
     * - done: 完成 ({type, requestId, traceId, answer, latencyMs})
     * - error: 错误 ({type, message})
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @param profile Profile 编码（LOVE / GENERAL / HERMES）
     * @return SseEmitter
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(Authentication authentication,
            @NotBlank @Size(max = 2000) @RequestParam String message,
            @NotBlank @Size(max = 64) @RequestParam String chatId,
            @NotNull @RequestParam AgentProfileCode profile,
            @RequestParam(defaultValue = "true") boolean memoryReadEnabled,
            @RequestParam(defaultValue = "true") boolean memoryWriteEnabled,
            @RequestParam(defaultValue = "CHAT") AgentRunMode mode,
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) List<String> attachmentIds
    ) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        
        long userId = currentUser(authentication);
        String requestId = requestGuard.generateRequestId();
        String traceId = requestGuard.generateTraceId();
        conversationService.ensure(userId, chatId, profile);
        var safety = promptInjectionGuard.inspect(userId, chatId, requestId, message, "USER_MESSAGE");
        if (safety.blocked()) {
            try { emitter.send(SseEmitter.event().name("security").data(safety.publicView())); emitter.send(SseEmitter.event().name("error").data(Map.of("type", "error", "message", safety.userMessage()))); emitter.complete(); }
            catch (Exception e) { emitter.completeWithError(e); }
            return emitter;
        }
        String safeMessage = safety.sanitized() ? promptInjectionGuard.sanitize(message) : message;
        String attachmentText = attachmentService.contextText(userId, chatId, attachmentIds);
        var start = conversationEvents.startTurn(userId, chatId, requestId, safeMessage,
                Map.of("profile", profile.name(), "legacyStream", true));
        if (!start.started()) {
            streamConflict(emitter, start, requestId);
            return emitter;
        }
        
        log.info("[{}] SSE stream request: profile={}, chatId={}", requestId, profile, chatId);
        
        // 查找 Profile
        AgentProfile agentProfile = agentProfiles.stream()
                .filter(p -> p.code() == profile)
                .findFirst()
                .orElse(null);
        
        if (agentProfile == null) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("type", "error", "message", "Unknown profile: " + profile)));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        
        // 从 PostgreSQL 事件流加载摘要、工作记忆和完整 Turn。
        List<Message> history = List.of();
        Map<String, Object> metadata = new HashMap<>();
        try {
            var slice = conversationContext.prepare(userId, chatId, requestId, safeMessage);
            history = slice.messages();
            slice.contributeTo(metadata);
        } catch (Exception e) {
            log.error("[{}] Failed to load history for chatId={}: {}", requestId, chatId, e.getMessage());
            conversationEvents.failTurn(chatId, requestId, "FAILED", "CONTEXT_LOAD_FAILED");
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "type", "error", "message", "无法构建对话上下文，请稍后重试")));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
            return emitter;
        }
        metadata.put("requestId", requestId);
        metadata.put("userId", userId);
        metadata.put("memoryReadEnabled", memoryReadEnabled);
        metadata.put("memoryWriteEnabled", memoryWriteEnabled);
        metadata.put("promptSafety", safety);
        metadata.put("attachmentText", attachmentText);
        snapshots.begin(chatId, userId, requestId, traceId);
        
        // 构建上下文
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .chatId(chatId)
                .profile(agentProfile)
                .userMessage(safeMessage)
                .history(history)
                .metadata(metadata)
                .runMode(mode)
                .planId(planId)
                .build();
        
        // 异步执行流式 Tool-Use Loop
        toolUseLoopRuntime.executeStream(context, emitter);
        
        emitter.onTimeout(() -> {
            log.warn("[{}] SSE connection timeout", requestId);
            conversationEvents.failTurn(chatId, requestId, "CANCELLED", "SSE_TIMEOUT");
            snapshots.completeByRequest(requestId, "FAILED", "SSE_TIMEOUT");
            emitter.complete();
        });
        emitter.onCompletion(() -> log.info("[{}] SSE connection completed", requestId));
        
        return emitter;
    }

    /** Preferred streaming endpoint: token travels in Authorization header, never in the URL. */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamPost(Authentication authentication, @RequestBody @Validated AgentChatBody body) {
        SseEmitter emitter = new SseEmitter(300_000L);
        long userId = currentUser(authentication);
        String requestId = body.requestId() == null || body.requestId().isBlank() ? requestGuard.generateRequestId() : body.requestId();
        String traceId = requestGuard.generateTraceId();
        conversationService.ensure(userId, body.chatId(), body.profile());
        var safety = promptInjectionGuard.inspect(userId, body.chatId(), requestId, body.message(), "USER_MESSAGE");
        if (safety.blocked()) {
            try { emitter.send(SseEmitter.event().name("security").data(safety.publicView())); emitter.send(SseEmitter.event().name("error").data(Map.of("type", "error", "message", safety.userMessage()))); emitter.complete(); }
            catch (Exception e) { emitter.completeWithError(e); }
            return emitter;
        }
        String safeMessage = safety.sanitized() ? promptInjectionGuard.sanitize(body.message()) : body.message();
        String attachmentText = attachmentService.contextText(userId, body.chatId(), body.attachmentIds());
        KnowledgeRetrievalResult knowledgeResult = knowledge(body.chatId(), userId,
                body.knowledgeDocumentIds(), safeMessage);
        var start = conversationEvents.startTurn(userId, body.chatId(), requestId, safeMessage,
                Map.of("profile", body.profile().name()));
        if (!start.started()) {
            streamConflict(emitter, start, requestId);
            return emitter;
        }
        AgentProfile profile = agentProfiles.stream().filter(p -> p.code() == body.profile()).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown profile"));
        ConversationContextService.ContextSlice slice;
        try {
            slice = conversationContext.prepare(userId, body.chatId(), requestId, safeMessage);
        } catch (Exception e) {
            conversationEvents.failTurn(body.chatId(), requestId, "FAILED", "CONTEXT_LOAD_FAILED");
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "type", "error", "message", "无法构建对话上下文，请稍后重试")));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
            return emitter;
        }
        List<Message> history = slice.messages();
        Map<String, Object> metadata = new HashMap<>(body.metadata() == null ? Map.of() : body.metadata());
        slice.contributeTo(metadata);
        metadata.put("requestId", requestId);
        metadata.put("userId", userId);
        metadata.put("enabledTools", body.enabledTools() == null ? List.of() : body.enabledTools());
        metadata.put("webSearchEnabled", Boolean.TRUE.equals(body.webSearchEnabled()));
        metadata.put("knowledgeDocumentIds", body.knowledgeDocumentIds() == null ? List.of() : body.knowledgeDocumentIds());
        metadata.put("knowledgeResult", knowledgeResult);
        metadata.put("attachmentText", attachmentText);
        metadata.put("memoryReadEnabled", !Boolean.FALSE.equals(body.memoryReadEnabled()));
        metadata.put("memoryWriteEnabled", !Boolean.FALSE.equals(body.memoryWriteEnabled()));
        metadata.put("promptSafety", safety);
        snapshots.begin(body.chatId(), userId, requestId, traceId);
        toolUseLoopRuntime.executeStream(AgentContext.builder().requestId(requestId).traceId(traceId).chatId(body.chatId()).profile(profile).userMessage(safeMessage).history(history).metadata(metadata).runMode(body.mode() == null ? AgentRunMode.CHAT : body.mode()).planId(body.planId()).build(), emitter);
        emitter.onTimeout(() -> {
            conversationEvents.failTurn(body.chatId(), requestId, "CANCELLED", "SSE_TIMEOUT");
            snapshots.completeByRequest(requestId, "FAILED", "SSE_TIMEOUT");
            emitter.complete();
        });
        return emitter;
    }
    
    /**
     * 清除会话记忆
     *
     * @param chatId 会话 ID
     */
    @DeleteMapping("/chat/{chatId}/memory")
    public Result<Void> clearMemory(Authentication authentication, @NotBlank @PathVariable String chatId) {
        log.info("Clearing memory for chatId={}", chatId);
        conversationService.clearMessages(currentUser(authentication), chatId);
        return Result.ok(null);
    }

    /**
     * 获取已注册的 Profile 列表
     */
    @GetMapping("/profiles")
    public Result<java.util.Set<AgentProfileCode>> profiles() {
        return Result.ok(agentOrchestrator.getRegisteredProfiles());
    }
    @GetMapping("/tools")
    public Result<List<Map<String, Object>>> tools(@RequestParam AgentProfileCode profile) {
        return Result.ok(toolRegistry.getEnabledTools(profile).stream().map(tool -> Map.<String,Object>of(
                "name", tool.name(), "description", tool.description(), "riskLevel", tool.riskLevel().name(), "requiresApproval", tool.requiresApproval(),
                "networkRequired", tool.name().equals("searchWeb") || tool.name().equals("scrapeWebPage"),
                "available", !(tool.name().equals("searchWeb") || tool.name().equals("scrapeWebPage")) || !searchApiKey.isBlank())).toList());
    }
    private KnowledgeRetrievalResult knowledge(String chatId, long userId, List<String> documentIds, String query) {
        if (documentIds == null || documentIds.isEmpty()) return KnowledgeRetrievalResult.empty(query);
        // Repository query rechecks owner/document ids; no user-provided context is trusted.
        return knowledgeRetrievalService.retrieveForUser(userId, documentIds, query, 5, .35);
    }
    private Result<AgentResponse> turnConflict(ConversationEventService.TurnStartResult start, String requestId) {
        if (start.duplicateCompleted()) {
            return Result.ok(AgentResponse.builder().requestId(requestId).traceId("")
                    .answer(start.existingAnswer()).citations(List.of()).toolCalls(List.of()).latencyMs(0).build());
        }
        String message = start.status() == ConversationEventService.TurnStartStatus.BUSY
                ? "当前会话已有任务正在执行，请等待完成后重试"
                : "该请求正在处理中，请使用相同 requestId 查询结果";
        return Result.error(409, message);
    }
    private void streamConflict(SseEmitter emitter, ConversationEventService.TurnStartResult start,
                                String requestId) {
        try {
            if (start.duplicateCompleted()) {
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "type", "done", "requestId", requestId, "traceId", "",
                        "answer", start.existingAnswer(), "latencyMs", 0)));
            } else {
                String message = start.status() == ConversationEventService.TurnStartStatus.BUSY
                        ? "当前会话已有任务正在执行，请等待完成后重试"
                        : "该请求正在处理中，请稍后查询结果";
                emitter.send(SseEmitter.event().name("error").data(Map.of("type", "error", "message", message)));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
    private static long currentUser(Authentication authentication) { return ((AuthenticatedUser) authentication.getPrincipal()).userId(); }
    
    /**
     * 请求体
     */
    public record AgentChatBody(
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64) String chatId,
            @NotNull AgentProfileCode profile,
            AgentRunMode mode,
            String planId,
            List<String> enabledTools,
            Boolean webSearchEnabled,
            List<String> knowledgeDocumentIds,
            List<String> attachmentIds,
            Map<String, Object> metadata,
            String requestId,
            Boolean memoryReadEnabled,
            Boolean memoryWriteEnabled
    ) {
        public AgentChatBody(String message, String chatId, AgentProfileCode profile) {
            this(message, chatId, profile, AgentRunMode.CHAT, null, List.of(), false, List.of(), List.of(), Map.of(), null, true, true);
        }
    }
}
