package com.sxw.sxwaiagent.agent.runtime;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptAssembler;
import com.sxw.sxwaiagent.agent.prompt.PromptRunRecorder;
import com.sxw.sxwaiagent.agent.tool.ToolExecutor;
import com.sxw.sxwaiagent.agent.tool.ToolResult;
import com.sxw.sxwaiagent.common.web.ClientAbortDetector;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Tool-Use Loop 运行时
 * <p>
 * 基于 Claude Code 的 Tool-Use Loop 设计：
 * - 模型返回 tool_use 时，执行工具并继续循环
 * - 模型返回 end_turn 时，结束循环
 * - 减少 Thought 文本开销，专注于工具调用
 * <p>
 * 相比 ReAct（Think-Act 循环），Tool-Use Loop 更加直接：
 * - 不需要解析 "Thought: ..." 文本
 * - 不需要显式的 Action 标识
 * - 模型直接返回工具调用请求，运行时自动执行
 * <p>
 * 集成 PromptAssembler + ContextAssembler：
 * - 每次 LLM 调用前通过 PromptAssembler 组装完整 SystemMessage
 * - 包含 Memory、Knowledge、工具结果等动态 Section
 * - 通过 PromptRunRecorder 记录 Prompt 元数据（哈希、版本、长度）
 * <p>
 * 弹性设计（Resilience4j）：
 * - 每次 LLM 调用带超时控制（{@code sxw.agent.runtime.llm-timeout-seconds}）
 * - null 响应自动指数退避重试（{@code sxw.agent.runtime.max-retries}）
 * - CircuitBreaker OPEN 时返回友好降级消息，不直接抛异常
 */
@Component
public class ToolUseLoopRuntime implements AgentRuntime {
    
    private static final Logger log = LoggerFactory.getLogger(ToolUseLoopRuntime.class);
    
    private static final int MAX_HISTORY_MESSAGES = 100;
    private static final String RESILIENCE_INSTANCE = "dashscope";
    
    private final ChatModel chatModel;
    private final ToolExecutor toolExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final PromptAssembler promptAssembler;
    private final PromptRunRecorder promptRunRecorder;
    private final Executor agentTaskExecutor;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    
    @Value("${sxw.agent.runtime.max-turns:10}")
    private int maxTurns;
    
    @Value("${sxw.agent.runtime.llm-timeout-seconds:60}")
    private int llmTimeoutSeconds;
    
    @Value("${sxw.agent.runtime.max-retries:3}")
    private int maxRetries;
    
    public ToolUseLoopRuntime(
            ChatModel chatModel,
            ToolExecutor toolExecutor,
            ApplicationEventPublisher eventPublisher,
            PromptAssembler promptAssembler,
            PromptRunRecorder promptRunRecorder,
            @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.chatModel = chatModel;
        this.toolExecutor = toolExecutor;
        this.eventPublisher = eventPublisher;
        this.promptAssembler = promptAssembler;
        this.promptRunRecorder = promptRunRecorder;
        this.agentTaskExecutor = agentTaskExecutor;
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
    }
    
    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();
        
        AgentProfile profile = context.profile();
        log.info("[{}] ToolUseLoopRuntime executing for profile={}", context.requestId(), profile.code());
        
        // 历史消息（裁剪防止溢出）
        List<Message> trimmedHistory = null;
        if (context.history() != null) {
            trimmedHistory = trimHistory(context.history(), MAX_HISTORY_MESSAGES);
        }
        
        // 执行 Tool-Use Loop
        List<AgentResponse.ToolCallInfo> toolCalls = new ArrayList<>();
        List<ToolResult> accumulatedToolResults = new ArrayList<>();
        String finalAnswer = "";
        int turn = 0;
        
        while (turn < maxTurns) {
            turn++;
            log.info("[{}] Turn {}/{}", context.requestId(), turn, maxTurns);
            
            // 每轮重新组装 Prompt（动态 section 随工具结果变化）
            AgentContext loopContext = buildLoopContext(context, accumulatedToolResults);
            AssembledPrompt assembledPrompt = promptAssembler.assemble(profile, loopContext);
            
            // 记录 Prompt 元数据
            promptRunRecorder.record(context.requestId(), profile.code().name(), turn, assembledPrompt);
            log.debug("[{}] Turn {} prompt: sections={}, rendered={} chars, staticHash={}, dynamicHash={}",
                    context.requestId(), turn,
                    assembledPrompt.sections().size(),
                    assembledPrompt.renderedLength(),
                    assembledPrompt.staticHash().substring(0, Math.min(8, assembledPrompt.staticHash().length())),
                    assembledPrompt.dynamicHash().substring(0, Math.min(8, assembledPrompt.dynamicHash().length())));
            
            // 构建消息列表
            List<Message> messages = new ArrayList<>();
            
            // 1. System Message（来自 PromptAssembler，含 Memory、Knowledge 等 section）
            messages.add(new SystemMessage(assembledPrompt.rendered()));
            
            // 2. 历史消息
            if (trimmedHistory != null) {
                messages.addAll(trimmedHistory);
            }
            
            // 3. 当前用户消息
            messages.add(new UserMessage(context.userMessage()));
            
            // 4. 工具调用的 Assistant + ToolResponse 消息（来自上一轮）
            messages.addAll(buildToolMessages(toolCalls));
            
            // 弹性调用 LLM（超时 + 重试 + 熔断）
            Prompt prompt = new Prompt(messages);
            ChatResponse chatResponse = resilientLlmCall(prompt, context.requestId(), turn);
            
            if (chatResponse == null) {
                // 所有重试耗尽或不可恢复错误，降级返回
                long latencyMs = System.currentTimeMillis() - startTime;
                AgentResponse fallback = buildFallbackResponse(
                        context, profile, toolCalls, latencyMs,
                        "LLM 调用失败，已重试 " + maxRetries + " 次");
                log.warn("[{}] LLM call failed after retries at turn {}, returning fallback",
                        context.requestId(), turn);
                return fallback;
            }
            
            Generation generation = chatResponse.getResult();
            AssistantMessage assistantMessage = generation.getOutput();
            
            // 检查是否有工具调用
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                // 有工具调用，执行工具
                for (var toolCall : assistantMessage.getToolCalls()) {
                    log.info("[{}] Tool call: {}({})", context.requestId(), toolCall.name(), toolCall.arguments());
                    
                    // 检查工具是否在 Profile 允许列表中
                    if (!profile.enabledToolNames().contains(toolCall.name())) {
                        log.warn("[{}] Tool {} is not enabled for profile {}",
                                context.requestId(), toolCall.name(), profile.code());
                        toolCalls.add(new AgentResponse.ToolCallInfo(
                                toolCall.name(),
                                toolCall.arguments(),
                                "Error: Tool " + toolCall.name() + " is not available for this profile."
                        ));
                        continue;
                    }
                    
                    // 执行工具（带 Profile 风险评估）
                    ToolResult toolResult = toolExecutor.execute(
                            toolCall.name(),
                            toolCall.arguments(),
                            context.requestId(),
                            context.traceId(),
                            turn,
                            profile
                    );
                    
                    // 记录工具调用信息
                    toolCalls.add(new AgentResponse.ToolCallInfo(
                            toolCall.name(),
                            toolCall.arguments(),
                            toolResult.content()
                    ));
                    
                    // 累积工具结果，下一轮 PromptAssembler 重新组装时可用
                    accumulatedToolResults.add(toolResult);
                }
                
                // 继续循环
                continue;
            }
            
            // 没有工具调用，模型返回最终答案
            finalAnswer = assistantMessage.getText();
            log.info("[{}] Model returned final answer (turn {})", context.requestId(), turn);
            break;
        }
        
        if (turn >= maxTurns) {
            log.warn("[{}] Reached max turns ({})", context.requestId(), maxTurns);
            finalAnswer = "抱歉，我在处理您的请求时达到了最大轮次限制。请尝试简化您的请求。";
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        AgentResponse response = AgentResponse.builder()
                .requestId(context.requestId())
                .traceId(context.traceId())
                .answer(finalAnswer)
                .citations(List.of())
                .toolCalls(toolCalls)
                .latencyMs(latencyMs)
                .build();
        
        // 发布 AgentRunCompletedEvent
        eventPublisher.publishEvent(new AgentRunCompletedEvent(
                this,
                context.requestId(),
                context.traceId(),
                profile.code().name(),
                response
        ));
        
        return response;
    }
    
    /**
     * 流式执行 Agent 任务（SSE 输出）
     * <p>
     * 与 {@link #execute(AgentContext)} 相同的 Tool-Use Loop 逻辑，
     * 但 LLM 调用使用 chatModel.stream() 实现逐 token 流式输出。
     * 工具调用仍然同步执行，结果通过 SSE 事件通知前端。
     *
     * @param context Agent 执行上下文
     * @param emitter SseEmitter（由 Controller 创建并返回）
     */
    public void executeStream(AgentContext context, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> doExecuteStream(context, emitter), agentTaskExecutor);
    }
    
    private void doExecuteStream(AgentContext context, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        AgentProfile profile = context.profile();
        log.info("[{}] ToolUseLoopRuntime streaming for profile={}", context.requestId(), profile.code());
        
        List<Message> trimmedHistory = null;
        if (context.history() != null) {
            trimmedHistory = trimHistory(context.history(), MAX_HISTORY_MESSAGES);
        }
        
        List<AgentResponse.ToolCallInfo> toolCalls = new ArrayList<>();
        List<ToolResult> accumulatedToolResults = new ArrayList<>();
        String finalAnswer = "";
        int turn = 0;
        
        try {
            while (turn < maxTurns) {
                turn++;
                log.info("[{}] Stream Turn {}/{}", context.requestId(), turn, maxTurns);
                
                AgentContext loopContext = buildLoopContext(context, accumulatedToolResults);
                AssembledPrompt assembledPrompt = promptAssembler.assemble(profile, loopContext);
                promptRunRecorder.record(context.requestId(), profile.code().name(), turn, assembledPrompt);
                
                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(assembledPrompt.rendered()));
                if (trimmedHistory != null) {
                    messages.addAll(trimmedHistory);
                }
                messages.add(new UserMessage(context.userMessage()));
                messages.addAll(buildToolMessages(toolCalls));
                
                // 弹性流式调用 LLM（超时 + 熔断）
                Prompt prompt = new Prompt(messages);
                Flux<ChatResponse> responseFlux = resilientStreamCall(prompt, context.requestId(), turn);
                
                if (responseFlux == null) {
                    // 降级：发送错误 token 并结束
                    String fallbackMsg = "抱歉，AI 服务暂时不可用，请稍后再试。";
                    log.warn("[{}] Stream LLM call failed at turn {}, returning fallback",
                            context.requestId(), turn);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(Map.of("type", "token", "content", fallbackMsg)));
                    } catch (IOException ignored) { }
                    finalAnswer = fallbackMsg;
                    break;
                }
                
                // 收集流式响应
                StringBuilder textBuilder = new StringBuilder();
                List<AssistantMessage.ToolCall> collectedToolCalls = new ArrayList<>();
                // 用于跨 chunk 累积同一个工具调用
                StringBuilder toolCallIdBuilder = new StringBuilder();
                StringBuilder toolCallNameBuilder = new StringBuilder();
                StringBuilder toolCallArgsBuilder = new StringBuilder();
                boolean[] hasToolCall = {false};
                
                responseFlux.doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null) return;
                    AssistantMessage output = chunk.getResult().getOutput();
                    
                    // 发送文本 token
                    String text = output.getText();
                    if (text != null && !text.isEmpty()) {
                        textBuilder.append(text);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(Map.of("type", "token", "content", text)));
                        } catch (IOException e) {
                            throw new SseIOException(e);
                        }
                    }
                    
                    // 收集工具调用 delta
                    if (output.getToolCalls() != null) {
                        for (var tc : output.getToolCalls()) {
                            hasToolCall[0] = true;
                            if (tc.id() != null && !tc.id().isEmpty()) {
                                // 新的工具调用开始，保存之前的（如果有）
                                flushToolCall(collectedToolCalls, toolCallIdBuilder,
                                        toolCallNameBuilder, toolCallArgsBuilder);
                                toolCallIdBuilder.append(tc.id());
                            }
                            if (tc.name() != null) toolCallNameBuilder.append(tc.name());
                            if (tc.arguments() != null) toolCallArgsBuilder.append(tc.arguments());
                        }
                    }
                }).blockLast();
                
                // 刷新最后一个未完成的工具调用
                if (hasToolCall[0]) {
                    flushToolCall(collectedToolCalls, toolCallIdBuilder, toolCallNameBuilder, toolCallArgsBuilder);
                }
                
                // 处理工具调用
                if (!collectedToolCalls.isEmpty()) {
                    for (var toolCall : collectedToolCalls) {
                        log.info("[{}] Stream tool call: {}({})", 
                                context.requestId(), toolCall.name(), toolCall.arguments());
                        
                        // 通知前端正在调用工具
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("tool_call")
                                    .data(Map.of("type", "tool_call", "toolName", 
                                            toolCall.name() != null ? toolCall.name() : "unknown",
                                            "arguments", toolCall.arguments() != null ? toolCall.arguments() : "")));
                        } catch (IOException e) {
                            throw new SseIOException(e);
                        }
                        
                        if (!profile.enabledToolNames().contains(toolCall.name())) {
                            log.warn("[{}] Tool {} is not enabled for profile {}",
                                    context.requestId(), toolCall.name(), profile.code());
                            toolCalls.add(new AgentResponse.ToolCallInfo(
                                    toolCall.name(), toolCall.arguments(),
                                    "Error: Tool " + toolCall.name() + " is not available for this profile."
                            ));
                            continue;
                        }
                        
                        ToolResult toolResult = toolExecutor.execute(
                                toolCall.name(), toolCall.arguments(),
                                context.requestId(), context.traceId(), turn, profile
                        );
                        
                        toolCalls.add(new AgentResponse.ToolCallInfo(
                                toolCall.name(), toolCall.arguments(), toolResult.content()
                        ));
                        accumulatedToolResults.add(toolResult);
                        
                        // 通知前端工具执行结果
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("tool_result")
                                    .data(Map.of("type", "tool_result", "toolName", 
                                            toolCall.name() != null ? toolCall.name() : "unknown",
                                            "result", toolResult.content() != null ? toolResult.content() : "")));
                        } catch (IOException e) {
                            throw new SseIOException(e);
                        }
                    }
                    continue;
                }
                
                // 最终答案（流式文本已经发送，这里记录完整文本）
                // 如果 Flux 因超时/错误返回空（无文本、无工具调用），降级处理
                if (textBuilder.isEmpty() && collectedToolCalls.isEmpty()) {
                    finalAnswer = "抱歉，AI 服务暂时不可用，请稍后再试。";
                    log.warn("[{}] Stream returned empty at turn {}, using fallback", context.requestId(), turn);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(Map.of("type", "token", "content", finalAnswer)));
                    } catch (IOException ignored) { }
                } else {
                    finalAnswer = textBuilder.toString();
                    log.info("[{}] Stream model returned final answer (turn {})", context.requestId(), turn);
                }
                break;
            }
            
            if (turn >= maxTurns) {
                log.warn("[{}] Stream reached max turns ({})", context.requestId(), maxTurns);
                finalAnswer = "抱歉，我在处理您的请求时达到了最大轮次限制。请尝试简化您的请求。";
            }
            
            long latencyMs = System.currentTimeMillis() - startTime;
            
            // 发送完成事件
            try {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of(
                                "type", "done",
                                "requestId", context.requestId(),
                                "traceId", context.traceId() != null ? context.traceId() : "",
                                "answer", finalAnswer,
                                "latencyMs", latencyMs
                        )));
                emitter.complete();
            } catch (IOException e) {
                if (ClientAbortDetector.isClientAbort(e)) {
                    log.info("[{}] SSE client disconnected during completion", context.requestId());
                } else {
                    log.warn("[{}] SSE complete failed: {}", context.requestId(), e.getMessage());
                }
            }
            
            // 发布事件
            AgentResponse response = AgentResponse.builder()
                    .requestId(context.requestId())
                    .traceId(context.traceId())
                    .answer(finalAnswer)
                    .citations(List.of())
                    .toolCalls(toolCalls)
                    .latencyMs(latencyMs)
                    .build();
            eventPublisher.publishEvent(new AgentRunCompletedEvent(
                    this, context.requestId(), context.traceId(),
                    profile.code().name(), response
            ));
            
        } catch (SseIOException e) {
            Throwable cause = e.getCause();
            if (ClientAbortDetector.isClientAbort(cause)) {
                log.info("[{}] SSE client disconnected", context.requestId());
            } else {
                log.warn("[{}] SSE send error: {}", context.requestId(), cause.getMessage());
            }
            try { emitter.complete(); } catch (RuntimeException ignore) { }
        } catch (Exception e) {
            if (ClientAbortDetector.isClientAbort(e)) {
                log.info("[{}] SSE client disconnected", context.requestId());
                try { emitter.complete(); } catch (RuntimeException ignore) { }
                return;
            }
            log.error("[{}] Stream execution error", context.requestId(), e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("type", "error", "message", "执行错误: " + e.getMessage())));
                emitter.complete();
            } catch (Exception ex) {
                if (ClientAbortDetector.isClientAbort(ex)) {
                    log.info("[{}] SSE client disconnected during error handling", context.requestId());
                } else {
                    emitter.completeWithError(ex);
                }
            }
        }
    }
    
    // ───────────────────────── Resilience helpers ─────────────────────────
    
    /**
     * 弹性同步 LLM 调用：超时 → Retry → CircuitBreaker
     * <p>
     * 装饰链（由内向外）：timeout → Retry → CircuitBreaker
     * <ul>
     *   <li>超时：通过 {@code CompletableFuture.orTimeout()} 控制单次 LLM 调用时长</li>
     *   <li>Retry：指数退避重试 null 响应和可恢复异常</li>
     *   <li>CircuitBreaker：OPEN 时快速失败，不再重试</li>
     * </ul>
     *
     * @return ChatResponse（正常时），null（所有重试耗尽或不可恢复错误时）
     */
    private ChatResponse resilientLlmCall(Prompt prompt, String requestId, int turn) {
        // 1. 熔断器快速检查：OPEN 时直接返回 null，避免无意义等待
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("[{}] CircuitBreaker OPEN, skipping LLM call at turn {}", requestId, turn);
            return null;
        }
        
        // 2. 构建带超时的调用
        Supplier<ChatResponse> timedCall = () -> {
            try {
                return CompletableFuture.supplyAsync(() -> chatModel.call(prompt), agentTaskExecutor)
                        .orTimeout(llmTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
            } catch (java.util.concurrent.CompletionException e) {
                if (e.getCause() instanceof TimeoutException) {
                    throw new RuntimeException("LLM call timed out after " + llmTimeoutSeconds + "s", e.getCause());
                }
                throw (e.getCause() instanceof RuntimeException re) ? re : new RuntimeException(e.getCause());
            }
        };
        
        // 3. 装饰链：CB(Retry(timeout(call)))
        Supplier<ChatResponse> withRetry = Retry.decorateSupplier(retry, timedCall);
        Supplier<ChatResponse> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);
        
        try {
            ChatResponse response = withCb.get();
            
            // null 响应视为失败，触发重试（通过主动抛异常让 Retry 感知）
            if (response == null || response.getResult() == null) {
                log.warn("[{}] LLM returned null response at turn {}, treating as failure", requestId, turn);
                // 已经出了 CB/Retry 装饰链，此处手动重试
                for (int i = 1; i <= maxRetries; i++) {
                    log.info("[{}] Null-response retry attempt {}/{}", requestId, i, maxRetries);
                    try {
                        Thread.sleep((long) Math.pow(2, i) * 500); // 指数退避: 1s, 2s, 4s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    response = chatModel.call(prompt);
                    if (response != null && response.getResult() != null) {
                        log.info("[{}] Null-response retry succeeded at attempt {}", requestId, i);
                        return response;
                    }
                }
                log.warn("[{}] All {} null-response retries exhausted at turn {}", requestId, maxRetries, turn);
                return null;
            }
            
            return response;
        } catch (CallNotPermittedException e) {
            log.warn("[{}] CircuitBreaker OPEN, LLM call rejected at turn {}", requestId, turn);
            return null;
        } catch (Exception e) {
            log.error("[{}] LLM call failed at turn {} after resilience chain: {}",
                    requestId, turn, e.getMessage());
            return null;
        }
    }
    
    /**
     * 弹性流式 LLM 调用：超时 + CircuitBreaker
     * <p>
     * 流式调用不适合用 Retry（Flux 无法重放），仅使用超时 + 熔断保护。
     * 超时通过 Reactor 的 {@code .timeout()} 操作符实现。
     *
     * @return Flux<ChatResponse>（正常时），null（CB OPEN 或不可恢复错误时）
     */
    private Flux<ChatResponse> resilientStreamCall(Prompt prompt, String requestId, int turn) {
        // 1. 熔断器快速检查
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("[{}] CircuitBreaker OPEN, skipping stream LLM call at turn {}", requestId, turn);
            return null;
        }
        
        try {
            Flux<ChatResponse> flux = circuitBreaker.executeSupplier(() ->
                    chatModel.stream(prompt)
                            .timeout(Duration.ofSeconds(llmTimeoutSeconds))
            );
            
            // 添加错误处理：超时或 CB 异常时返回空 Flux
            // 注意：executeSupplier 在 chatModel.stream() 同步返回时已记录 CB success，
            // 异步阶段的超时/错误不会被 CB 自动感知，此处仅做日志和流终止
            return flux.onErrorResume(e -> {
                if (e instanceof TimeoutException || e instanceof java.util.concurrent.TimeoutException) {
                    log.warn("[{}] Stream LLM call timed out after {}s at turn {}",
                            requestId, llmTimeoutSeconds, turn);
                } else if (e instanceof CallNotPermittedException) {
                    log.warn("[{}] Stream LLM call rejected by CircuitBreaker at turn {}", requestId, turn);
                } else {
                    log.error("[{}] Stream LLM call error at turn {}: {}", requestId, turn, e.getMessage());
                }
                return Flux.empty();
            });
        } catch (CallNotPermittedException e) {
            log.warn("[{}] CircuitBreaker OPEN, stream LLM call rejected at turn {}", requestId, turn);
            return null;
        } catch (Exception e) {
            log.error("[{}] Stream LLM call failed at turn {}: {}", requestId, turn, e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建降级响应：当 LLM 调用无法恢复时使用
     */
    private AgentResponse buildFallbackResponse(
            AgentContext context,
            AgentProfile profile,
            List<AgentResponse.ToolCallInfo> toolCalls,
            long latencyMs,
            String reason
    ) {
        String fallbackAnswer = "抱歉，AI 服务暂时不可用（" + reason + "），请稍后再试。";
        
        AgentResponse response = AgentResponse.builder()
                .requestId(context.requestId())
                .traceId(context.traceId())
                .answer(fallbackAnswer)
                .citations(List.of())
                .toolCalls(toolCalls)
                .latencyMs(latencyMs)
                .build();
        
        // 发布事件，让追踪系统感知降级
        eventPublisher.publishEvent(new AgentRunCompletedEvent(
                this,
                context.requestId(),
                context.traceId(),
                profile.code().name(),
                response
        ));
        
        return response;
    }
    
    // ───────────────────────── Internal helpers ─────────────────────────
    
    /**
     * 将跨 chunk 累积的工具调用 delta 刷新为一个完整的 ToolCall
     */
    private void flushToolCall(
            List<AssistantMessage.ToolCall> collected,
            StringBuilder idBuilder,
            StringBuilder nameBuilder,
            StringBuilder argsBuilder
    ) {
        if (nameBuilder.length() > 0 || idBuilder.length() > 0) {
            collected.add(new AssistantMessage.ToolCall(
                    idBuilder.toString(),
                    "function",
                    nameBuilder.toString(),
                    argsBuilder.toString()
            ));
            idBuilder.setLength(0);
            nameBuilder.setLength(0);
            argsBuilder.setLength(0);
        }
    }
    
    /**
     * 内部异常：包装 SSE send 的 IOException，以便在 reactive 链中传播
     */
    private static class SseIOException extends RuntimeException {
        SseIOException(Throwable cause) { super(cause); }
    }
    
    /**
     * 构建包含最新工具结果的 AgentContext（不可变，每次循环创建新实例）
     */
    private AgentContext buildLoopContext(AgentContext original, List<ToolResult> toolResults) {
        Map<String, Object> metadata = new HashMap<>(
                original.metadata() != null ? original.metadata() : Map.of()
        );
        if (!toolResults.isEmpty()) {
            metadata.put("toolResults", toolResults);
        }
        return AgentContext.builder()
                .requestId(original.requestId())
                .traceId(original.traceId())
                .chatId(original.chatId())
                .profile(original.profile())
                .userMessage(original.userMessage())
                .history(original.history())
                .metadata(metadata)
                .build();
    }
    
    /**
     * 根据已执行的工具调用构建 ToolResponseMessage 列表
     * <p>
     * 每轮 LLM 调用需要携带之前工具调用的 AssistantMessage + ToolResponseMessage，
     * 让模型感知工具执行结果。
     */
    private List<Message> buildToolMessages(List<AgentResponse.ToolCallInfo> toolCalls) {
        // Tool-Use Loop 中工具结果通过 PromptAssembler 的 TOOL_RESULTS section 注入 SystemMessage，
        // 此处无需重复添加 ToolResponseMessage，避免信息冗余。
        // 保留此扩展点，供未来切换为 Claude tool_use/tool_result 模式时使用。
        return List.of();
    }
    
    /**
     * 裁剪历史消息，保留最近的 N 条消息，防止 context 溢出
     * <p>
     * 策略：保留最近的 N 条 User/Assistant 消息对，确保对话完整性
     */
    private List<Message> trimHistory(List<Message> history, int maxMessages) {
        if (history.size() <= maxMessages) {
            return history;
        }
        
        // 从末尾开始保留，确保 User/Assistant 配对完整
        List<Message> result = new ArrayList<>();
        int count = 0;
        
        for (int i = history.size() - 1; i >= 0 && count < maxMessages; i--) {
            result.add(0, history.get(i));
            count++;
        }
        
        if (history.size() > maxMessages) {
            log.warn("Trimmed history from {} to {} messages", history.size(), result.size());
        }
        
        return result;
    }
}
