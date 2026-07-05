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
 * Abstract base agent class for managing agent state and execution flow.
 *
 * Provides state transitions, memory management, and step-based execution loop.
 * Subclasses must implement the step method.
 */
@Getter
@Setter
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;

    /** Agent state - volatile ensures visibility across threads (async executor + main thread callbacks) */
    private volatile AgentState state = AgentState.IDLE;

    private volatile int currentStep = 0;
    private int maxSteps = 10;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();

    /** Max messages in conversation history; older paired messages are trimmed to avoid token overflow. */
    private int maxHistoryMessages = 60;

    /** Async executor: defaults to ForkJoinPool.commonPool(), Spring injects bounded named thread pool in production. */
    private Executor executor = ForkJoinPool.commonPool();

    /** Post-execution callback hook; {@link #onFinishedFired} ensures it runs only once globally. */
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
        } catch (RuntimeException ex) {
            log.warn("agent={} onFinished hook failed", name, ex);
        }
    }

    /** Safely trim history, keeping first SystemMessage and recent messages; avoids breaking ToolCall↔ToolResponse pairs. */
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

    // ────────────────────── Public execution loop ──────────────────────

    /** Validate + initialize: returns null if passed, otherwise returns error message. */
    private String validate(String safeUserPrompt) {
        if (this.state != AgentState.IDLE) {
            return "Cannot run agent from state: " + this.state;
        }
        if (StrUtil.isBlank(safeUserPrompt)) {
            return "Cannot run agent with empty user prompt";
        }
        return null;
    }

    /** Initialize run state and add user message to context. */
    private void initRun(String safeUserPrompt) {
        this.state = AgentState.RUNNING;
        traceEvent("run_start", null, safeUserPrompt, "", "ok", 0);
        messageList.add(new UserMessage(safeUserPrompt));
    }

    /** Core step loop; returns execution results. stepConsumer allows SSE path to push intermediate events. */
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

    /** Post-execution cleanup: trace + callback + cleanup. */
    private void finalizeRun() {
        finishTrace(state == AgentState.ERROR ? "error" : "finished");
        invokeOnFinishedSafely();
        this.cleanup();
    }

    @FunctionalInterface
    private interface StepConsumer {
        void accept(String stepResult);
    }

    // ────────────────────── Public API ──────────────────────

    /**
     * Run agent synchronously.
     */
    public String run(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        String error = validate(safeUserPrompt);
        if (error != null) throw new RuntimeException(error);
        initRun(safeUserPrompt);
        try {
            List<String> results = executeLoop(null);
            return String.join("\n", results);
        } catch (RuntimeException e) {
            state = AgentState.ERROR;
            traceEvent("error", null, safeUserPrompt, e.getMessage(), "error", 0);
            log.error("Error executing agent", e);
            return "Execution error: " + e.getMessage();
        } finally {
            finalizeRun();
        }
    }

    /**
     * Run agent with streaming (SSE output).
     */
    public SseEmitter runStream(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 min timeout

        CompletableFuture.runAsync(() -> {
            String error = validate(safeUserPrompt);
            if (error != null) {
                try {
                    sseEmitter.send("Error: " + error);
                    sseEmitter.complete();
                } catch (IOException e) {
                    handleSseError(sseEmitter, e);
                }
                return;
            }
            initRun(safeUserPrompt);
            try {
                executeLoop(stepResult -> {
                    try {
                        sseEmitter.send(SseEmitter.event().name("step").data(stepResult));
                    } catch (IOException e) {
                        throw new SseSendException(e);
                    }
                });
                // Send final answer
                String finalAnswer = extractFinalAssistantText();
                if (StrUtil.isNotBlank(finalAnswer)) {
                    sseEmitter.send(SseEmitter.event().name("final").data(finalAnswer));
                }
                sseEmitter.complete();
            } catch (SseSendException e) {
                Throwable cause = e.getCause();
                handleSseDisconnect(sseEmitter,
                        cause instanceof Exception ex ? ex : new RuntimeException(cause));
            } catch (IOException e) {
                handleSseDisconnect(sseEmitter, e);
            } catch (RuntimeException e) {
                if (ClientAbortDetector.isClientAbort(e)) {
                    handleSseDisconnect(sseEmitter, e);
                    return;
                }
                state = AgentState.ERROR;
                traceEvent("error", null, safeUserPrompt, e.getMessage(), "error", 0);
                log.error("Error executing agent", e);
                try {
                    sseEmitter.send(SseEmitter.event().name("error").data("Execution error: " + e.getMessage()));
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
        try { sseEmitter.complete(); } catch (RuntimeException ignore) { }
    }

    private void handleSseError(SseEmitter sseEmitter, IOException e) {
        if (ClientAbortDetector.isClientAbort(e)) {
            handleSseDisconnect(sseEmitter, e);
        } else {
            sseEmitter.completeWithError(e);
        }
    }

    /** Internal exception: wraps IOException from SSE send for propagation in executeLoop. */
    private static class SseSendException extends RuntimeException {
        SseSendException(Throwable cause) { super(cause); }
    }

    /** Define a single step */
    public abstract String step();

    /** Extract the latest non-empty assistant text from conversation history. */
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

    /** Cleanup resources; subclasses may override. */
    protected void cleanup() { }
}
