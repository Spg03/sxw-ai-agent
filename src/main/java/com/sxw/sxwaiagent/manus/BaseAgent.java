package com.sxw.sxwaiagent.manus;

import cn.hutool.core.util.StrUtil;
import com.sxw.sxwaiagent.common.web.ClientAbortDetector;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import com.sxw.sxwaiagent.manus.model.AgentState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Getter
@Setter
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;

    /** 代理状态 — volatile 保证多线程（异步 executor + 主线程回调）可见性 */
    private volatile AgentState state = AgentState.IDLE;

    private volatile int currentStep = 0;
    private int maxSteps = 10;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();

    /** 会话历史最大消息条数；超过后会安全裁剪最早的成对消息，避免 token 爆炸。 */
    private int maxHistoryMessages = 60;

    /** 异步执行器：默认走 ForkJoinPool.commonPool()，生产由 Spring 注入有界命名线程池。 */
    private Executor executor = ForkJoinPool.commonPool();

    /** 运行结束后的回调钩子，通过 {@link #onFinishedFired} 保证全局只跑一次。 */
    private Runnable onFinished;
    private final AtomicBoolean onFinishedFired = new AtomicBoolean(false);

    private AgentTraceStore agentTraceStore;
    private String traceId;

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public void enableTracing(AgentTraceStore agentTraceStore, String chatId) {
        this.agentTraceStore = agentTraceStore;
        this.traceId = agentTraceStore == null ? null : agentTraceStore.startRun(chatId);
    }

    protected void traceEvent(String phase, String toolName,
                              String inputSummary, String outputSummary,
                              String status, long latencyMs) {
        if (agentTraceStore == null || traceId == null) return;
        agentTraceStore.appendEvent(traceId, currentStep, phase, toolName,
                inputSummary, outputSummary, status, latencyMs);
    }

    protected void finishTrace(String status) {
        if (agentTraceStore == null || traceId == null) return;
        agentTraceStore.finishRun(traceId, status);
    }

    private void invokeOnFinishedSafely() {
        Runnable hook = this.onFinished;
        if (hook == null) return;
        if (!onFinishedFired.compareAndSet(false, true)) return;
        try {
            hook.run();
        } catch (Exception ex) {
            log.warn("agent={} onFinished hook failed: {}", name, ex.getMessage());
        }
    }

    /** 安全裁剪历史，保留首条 SystemMessage 与最近消息，避免破坏 ToolCall↔ToolResponse 配对。 */
    protected void trimHistoryIfNeeded() {
        int max = maxHistoryMessages;
        if (max <= 0) return;
        int size = messageList.size();
        if (size <= max) return;

        int prefixKeep = (!messageList.isEmpty()
                && messageList.get(0) instanceof org.springframework.ai.chat.messages.SystemMessage) ? 1 : 0;
        int dropFrom = prefixKeep;
        int dropTo = Math.min(size, dropFrom + (size - max));
        while (dropTo < size
                && messageList.get(dropTo) instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
            dropTo++;
        }
        if (dropTo > dropFrom) {
            log.warn("agent={} history trimmed: removed [{},{}), size {} -> {}",
                    name, dropFrom, dropTo, size, size - (dropTo - dropFrom));
            messageList.subList(dropFrom, dropTo).clear();
        }
    }

    // ────────────────────── 公共执行循环 ──────────────────────

    /** 校验 + 初始化：返回 null 表示通过，否则返回错误消息。 */
    private String validate(String safeUserPrompt) {
        if (this.state != AgentState.IDLE) {
            return "Cannot run agent from state: " + this.state;
        }
        if (StrUtil.isBlank(safeUserPrompt)) {
            return "Cannot run agent with empty user prompt";
        }
        return null;
    }

    /** 初始化运行状态并将用户消息加入上下文。 */
    private void initRun(String safeUserPrompt) {
        this.state = AgentState.RUNNING;
        traceEvent("run_start", null, safeUserPrompt, "", "ok", 0);
        messageList.add(new UserMessage(safeUserPrompt));
    }

    /** 核心步骤循环，返回执行结果列表。stepConsumer 允许流式路径额外推送中间事件。 */
    private List<String> executeLoop(StepConsumer stepConsumer) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
            int stepNumber = i + 1;
            currentStep = stepNumber;
            log.info("Executing step {}/{}", stepNumber, maxSteps);
            long stepStart = System.currentTimeMillis();
            String stepResult = step();
            traceEvent("step", null, "", stepResult, "ok", System.currentTimeMillis() - stepStart);
            String result = "Step " + stepNumber + ": " + stepResult;
            results.add(result);
            if (stepConsumer != null) {
                stepConsumer.accept(result);
            }
        }
        if (state != AgentState.FINISHED && currentStep >= maxSteps) {
            state = AgentState.FINISHED;
            String terminated = "Terminated: Reached max steps (" + maxSteps + ")";
            results.add(terminated);
            if (stepConsumer != null) {
                stepConsumer.accept(terminated);
            }
        }
        return results;
    }

    /** 运行结束后的收尾：trace + 回调 + cleanup。 */
    private void finalizeRun() {
        finishTrace(state == AgentState.ERROR ? "error" : "finished");
        invokeOnFinishedSafely();
        this.cleanup();
    }

    @FunctionalInterface
    private interface StepConsumer {
        void accept(String stepResult);
    }

    // ────────────────────── 公共 API ──────────────────────

    /**
     * 同步运行代理。
     */
    public String run(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        String error = validate(safeUserPrompt);
        if (error != null) throw new RuntimeException(error);
        initRun(safeUserPrompt);
        try {
            List<String> results = executeLoop(null);
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            traceEvent("error", null, safeUserPrompt, e.getMessage(), "error", 0);
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            finalizeRun();
        }
    }

    /**
     * 流式运行代理（SSE 输出）。
     */
    public SseEmitter runStream(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时

        CompletableFuture.runAsync(() -> {
            String error = validate(safeUserPrompt);
            if (error != null) {
                try {
                    sseEmitter.send("错误：" + error);
                    sseEmitter.complete();
                } catch (Exception e) {
                    handleSseError(sseEmitter, e);
                }
                return;
            }
            initRun(safeUserPrompt);
            try {
                executeLoop(stepResult -> {
                    try {
                        sseEmitter.send(SseEmitter.event().name("step").data(stepResult));
                    } catch (Exception e) {
                        throw new SseSendException(e);
                    }
                });
                // 发送最终答案
                String finalAnswer = extractFinalAssistantText();
                if (StrUtil.isNotBlank(finalAnswer)) {
                    sseEmitter.send(SseEmitter.event().name("final").data(finalAnswer));
                }
                sseEmitter.complete();
            } catch (SseSendException e) {
                Throwable cause = e.getCause();
                handleSseDisconnect(sseEmitter,
                        cause instanceof Exception ex ? ex : new RuntimeException(cause));
            } catch (Exception e) {
                if (ClientAbortDetector.isClientAbort(e)) {
                    handleSseDisconnect(sseEmitter, e);
                    return;
                }
                state = AgentState.ERROR;
                traceEvent("error", null, safeUserPrompt, e.getMessage(), "error", 0);
                log.error("error executing agent", e);
                try {
                    sseEmitter.send(SseEmitter.event().name("error").data("执行错误：" + e.getMessage()));
                    sseEmitter.complete();
                } catch (IOException ex) {
                    if (ClientAbortDetector.isClientAbort(ex)) {
                        handleSseDisconnect(sseEmitter, ex);
                    } else {
                        sseEmitter.completeWithError(ex);
                    }
                }
            } finally {
                finalizeRun();
            }
        }, executor);

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            traceEvent("error", null, "", "sse timeout", "error", 0);
            finalizeRun();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            finalizeRun();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    private void handleSseDisconnect(SseEmitter sseEmitter, Exception e) {
        state = AgentState.FINISHED;
        log.info("agent={} sse client disconnected: {}", name, e.getMessage());
        try { sseEmitter.complete(); } catch (Exception ignore) { }
    }

    private void handleSseError(SseEmitter sseEmitter, Exception e) {
        if (ClientAbortDetector.isClientAbort(e)) {
            handleSseDisconnect(sseEmitter, e);
        } else {
            sseEmitter.completeWithError(e);
        }
    }

    /** 内部异常：包装 SSE send 时的 IOException，便于在 executeLoop 中传播。 */
    private static class SseSendException extends RuntimeException {
        SseSendException(Throwable cause) { super(cause); }
    }

    /** 定义单个步骤 */
    public abstract String step();

    /** 从会话历史中提取最近一条不为空的助手文本。 */
    protected String extractFinalAssistantText() {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message m = messageList.get(i);
            if (m instanceof AssistantMessage am) {
                String text = am.getText();
                if (text != null && !text.isBlank()) return text;
            }
        }
        return "";
    }

    /** 清理资源，子类可重写。 */
    protected void cleanup() { }
}
