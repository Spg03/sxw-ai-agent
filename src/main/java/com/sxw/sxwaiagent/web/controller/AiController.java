package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.web.ClientAbortDetector;
import com.sxw.sxwaiagent.infrastructure.memory.ManusMemoryStore;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import com.sxw.sxwaiagent.manus.SxwManus;
import com.sxw.sxwaiagent.love.LoveApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/ai")
@Validated
@Slf4j
@Tag(name = "AI 对话", description = "AI 恋爱大师 & Manus 超级智能体")
public class AiController {

    private final LoveApp loveApp;
    private final ToolCallback[] allTools;
    private final ChatModel dashscopeChatModel;
    private final Executor agentTaskExecutor;
    private final SkillRegistry skillRegistry;
    private final ManusMemoryStore manusMemoryStore;
    private final AgentTraceStore agentTraceStore;

    public AiController(LoveApp loveApp,
                        ToolCallback[] allTools,
                        ChatModel dashscopeChatModel,
                        @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
                        SkillRegistry skillRegistry,
                        ManusMemoryStore manusMemoryStore,
                        AgentTraceStore agentTraceStore) {
        this.loveApp = loveApp;
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
        this.agentTaskExecutor = agentTaskExecutor;
        this.skillRegistry = skillRegistry;
        this.manusMemoryStore = manusMemoryStore;
        this.agentTraceStore = agentTraceStore;
    }

    @GetMapping("/love_app/chat/sync")
    @Operation(summary = "同步对话", description = "同步调用 AI 恋爱大师应用")
    public String doChatWithLoveAppSync(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID") @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChat(message, chatId);
    }

    @GetMapping("/love_app/chat/ragflow/sync")
    @Operation(summary = "RAGFlow 同步对话", description = "同步调用 RAGFlow 增强的 AI 恋爱大师应用")
    public String doChatWithLoveAppRagFlowSync(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID") @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChatWithRagFlow(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 流式对话", description = "SSE 流式调用 AI 恋爱大师应用")
    public Flux<String> doChatWithLoveAppSSE(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID") @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/server_sent_event")
    @Operation(summary = "ServerSentEvent 流式对话", description = "ServerSentEvent 流式调用 AI 恋爱大师应用")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID") @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/love_app/chat/sse_emitter")
    @Operation(summary = "SseEmitter 流式对话", description = "SseEmitter 流式调用 AI 恋爱大师应用")
    public SseEmitter doChatWithLoveAppServerSseEmitter(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID") @RequestParam @NotBlank @Size(max = 64) String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    if (chunk == null) return;
                    try {
                        sseEmitter.send(Objects.requireNonNull(chunk));
                    } catch (IOException e) {
                        if (ClientAbortDetector.isClientAbort(e)) {
                            log.info("love_app sse client disconnected: {}", e.getMessage());
                            sseEmitter.complete();
                        } else {
                            sseEmitter.completeWithError(e);
                        }
                    }
                }, ex -> {
                    if (ex == null) {
                        sseEmitter.completeWithError(new IllegalStateException("love_app stream error is null"));
                        return;
                    }
                    if (ClientAbortDetector.isClientAbort(ex)) {
                        log.info("love_app sse client disconnected during stream: {}", ex.getMessage());
                        sseEmitter.complete();
                    } else {
                        sseEmitter.completeWithError(Objects.requireNonNull(ex));
                    }
                }, sseEmitter::complete);
        return sseEmitter;
    }

    @GetMapping("/manus/chat")
    @Operation(summary = "Manus 流式对话", description = "流式调用 Manus 超级智能体，支持跨请求对话记忆")
    public SseEmitter doChatWithManus(
            @Parameter(description = "用户消息") @RequestParam @NotBlank @Size(max = 2000) String message,
            @Parameter(description = "会话ID（缺省 default）") @RequestParam(required = false) @Size(max = 64) String chatId) {
        String safeChatId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        SxwManus sxwManus = new SxwManus(allTools, dashscopeChatModel, skillRegistry);
        sxwManus.enableTracing(agentTraceStore, safeChatId);
        sxwManus.setExecutor(agentTaskExecutor);
        java.util.List<org.springframework.ai.chat.messages.Message> history = manusMemoryStore.load(safeChatId);
        if (!history.isEmpty()) {
            sxwManus.getMessageList().addAll(history);
        }
        log.info("manus memory loaded: chatId={} restored={} msgs", safeChatId, history.size());
        sxwManus.setOnFinished(() -> manusMemoryStore.save(safeChatId, sxwManus.getMessageList()));
        return sxwManus.runStream(message);
    }

    @DeleteMapping("/manus/memory/{chatId}")
    @Operation(summary = "清空会话记忆", description = "清空指定 Manus 会话的记忆")
    public String clearManusMemory(
            @Parameter(description = "会话ID") @NotBlank @Size(max = 64) @PathVariable String chatId) {
        manusMemoryStore.clear(chatId);
        return "ok";
    }
}
