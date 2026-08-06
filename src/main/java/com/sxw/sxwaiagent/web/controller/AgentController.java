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
import com.sxw.sxwaiagent.plan.AgentRunMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
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

    /*
     * Chat workspace backlog (frontend controls are displayed as disabled/TODO):
     * TODO: add multipart attachment upload and extract file content into AgentContext.
     * TODO: add a web-search provider abstraction with per-user authorization and audit logging.
     * TODO: add explicit knowledge-base selection, tool selection and task-mode request fields.
     * TODO: add pinned conversation and conversation metadata persistence scoped to the authenticated user.
     */
    
    private final AgentOrchestrator agentOrchestrator;
    private final ToolUseLoopRuntime toolUseLoopRuntime;
    private final RequestGuard requestGuard;
    private final ChatMemory chatMemory;
    private final List<AgentProfile> agentProfiles;
    private final ConversationService conversationService;
    private final ToolRegistry toolRegistry;
    private final AttachmentService attachmentService;
    
    public AgentController(
            AgentOrchestrator agentOrchestrator,
            ToolUseLoopRuntime toolUseLoopRuntime,
            RequestGuard requestGuard,
            ChatMemory agentChatMemory,
            List<AgentProfile> agentProfiles,
            ConversationService conversationService,
            ToolRegistry toolRegistry,
            AttachmentService attachmentService
    ) {
        this.agentOrchestrator = agentOrchestrator;
        this.toolUseLoopRuntime = toolUseLoopRuntime;
        this.requestGuard = requestGuard;
        this.chatMemory = agentChatMemory;
        this.agentProfiles = agentProfiles;
        this.conversationService = conversationService;
        this.toolRegistry = toolRegistry;
        this.attachmentService = attachmentService;
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
        conversationService.ensure(currentUser(authentication), chatId, profile);
        AgentRequest request = AgentRequest.builder()
                .chatId(chatId)
                .profile(profile)
                .message(message)
                .stream(false)
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
        conversationService.append(currentUser(authentication), body.chatId(), "USER", body.message());
        Map<String, Object> metadata = new HashMap<>(body.metadata() == null ? Map.of() : body.metadata());
        metadata.put("runMode", body.mode() == null ? AgentRunMode.CHAT.name() : body.mode().name());
        metadata.put("planId", body.planId());
        metadata.put("enabledTools", body.enabledTools() == null ? List.of() : body.enabledTools());
        metadata.put("webSearchEnabled", Boolean.TRUE.equals(body.webSearchEnabled()));
        metadata.put("attachmentIds", body.attachmentIds() == null ? List.of() : body.attachmentIds());
        metadata.put("userId", currentUser(authentication));
        metadata.put("attachmentText", attachmentService.contextText(currentUser(authentication), body.chatId(), body.attachmentIds()));
        AgentRequest request = AgentRequest.builder()
                .chatId(body.chatId())
                .profile(body.profile())
                .message(body.message())
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
            @NotNull @RequestParam AgentProfileCode profile
    ) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        
        long userId = currentUser(authentication);
        conversationService.ensure(userId, chatId, profile);
        conversationService.append(userId, chatId, "USER", message);
        String requestId = requestGuard.generateRequestId();
        String traceId = requestGuard.generateTraceId();
        
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
        
        // 加载历史消息
        List<Message> history = List.of();
        try {
            history = chatMemory.get(chatId);
        } catch (Exception e) {
            log.warn("[{}] Failed to load history for chatId={}: {}", requestId, chatId, e.getMessage());
        }
        
        // 构建上下文
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .chatId(chatId)
                .profile(agentProfile)
                .userMessage(message)
                .history(history)
                .build();
        
        // 异步执行流式 Tool-Use Loop
        toolUseLoopRuntime.executeStream(context, emitter);
        
        emitter.onTimeout(() -> {
            log.warn("[{}] SSE connection timeout", requestId);
            emitter.complete();
        });
        emitter.onCompletion(() -> log.info("[{}] SSE connection completed", requestId));
        
        return emitter;
    }
    
    /**
     * 清除会话记忆
     *
     * @param chatId 会话 ID
     */
    @DeleteMapping("/chat/{chatId}/memory")
    public Result<Void> clearMemory(Authentication authentication, @NotBlank @PathVariable String chatId) {
        conversationService.get(currentUser(authentication), chatId);
        log.info("Clearing memory for chatId={}", chatId);
        chatMemory.clear(chatId);
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
                "name", tool.name(), "description", tool.description(), "riskLevel", tool.riskLevel().name(), "requiresApproval", tool.requiresApproval())).toList());
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
            List<String> attachmentIds,
            Map<String, Object> metadata
    ) {
        public AgentChatBody(String message, String chatId, AgentProfileCode profile) {
            this(message, chatId, profile, AgentRunMode.CHAT, null, List.of(), false, List.of(), Map.of());
        }
    }
}
