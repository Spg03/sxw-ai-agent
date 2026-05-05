package com.sxw.sxwaiagent.manus;

import cn.hutool.core.util.StrUtil;
import com.sxw.sxwaiagent.manus.model.AgentState;
import lombok.Data;
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
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /**
     * 会话历史最大消息条数；超过后会安全裁剪最早的成对消息，避免 token 爆炸。
     * 设为 0 或负数表示关闭裁剪。
     */
    private int maxHistoryMessages = 60;

    /**
     * 异步执行器：默认走 ForkJoinPool.commonPool()，
     * 但生产路径会被 Spring 容器注入有界、命名、可观测的 {@code agentTaskExecutor}。
     */
    private Executor executor = ForkJoinPool.commonPool();

    /**
     * 运行结束后的回调钩子。
     * 供控制层在会话结束时持久化 {@code messageList}，实现跨请求记忆。
     * 调用点：同步 run 的 finally、流式 async 任务的 finally、SseEmitter 的 onCompletion / onTimeout；
     * 通过 {@link #onFinishedFired} 保证全局只跑一次，避免重复保存 / 重复覆盖。
     */
    private Runnable onFinished;
    private final AtomicBoolean onFinishedFired = new AtomicBoolean(false);

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    private void invokeOnFinishedSafely() {
        Runnable hook = this.onFinished;
        if (hook == null) return;
        if (!onFinishedFired.compareAndSet(false, true)) return; // 只跑一次
        try {
            hook.run();
        } catch (Exception ex) {
            log.warn("agent={} onFinished hook failed: {}", name, ex.getMessage());
        }
    }

    /**
     * 安全裁剪历史：保留首条 {@code SystemMessage}（若有）与最近的 {@link #maxHistoryMessages} 条。
     * 为避免破坏 ToolCall ↔ ToolResponse 的配对，新窗口的首条消息若是 {@code ToolResponseMessage}
     * （上一条工具调用已被裁掉），则继续向后丢弃直到对齐到正常消息边界。
     */
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

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(safeUserPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(safeUserPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、走一次持久化钩子，再清理资源
            invokeOnFinishedSafely();
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程；executor 默认 ForkJoinPool，生产由 Spring 注入
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(safeUserPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(safeUserPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环
                int loopedSteps = 0;
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    loopedSteps = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    // 作为“思考过程”中间事件输出到 SSE，前端能在 “思考过程”面板里折叠展示
                    sseEmitter.send(SseEmitter.event().name("step").data(result));
                }
                // 检查是否超出步骤限制（仅在从未主动 FINISHED 时才提示）
                boolean reachedMax = state != AgentState.FINISHED && loopedSteps >= maxSteps;
                if (reachedMax) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send(SseEmitter.event().name("step")
                            .data("执行结束：达到最大步骤（" + maxSteps + "）"));
                }
                // 发送 “final” 事件：从会话历史中取最后一条助手文本作为自然语言答案
                String finalAnswer = extractFinalAssistantText();
                if (StrUtil.isNotBlank(finalAnswer)) {
                    sseEmitter.send(SseEmitter.event().name("final").data(finalAnswer));
                }
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send(SseEmitter.event().name("error").data("执行错误：" + e.getMessage()));
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、企业持久化钩子优先在这里调用 —— 后面的 sseEmitter.onCompletion 可能因
                //   客户端提前断开、容器调度等原因不被触发，这里是确定性的路径。
                invokeOnFinishedSafely();
                this.cleanup();
            }
        }, executor);

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            invokeOnFinishedSafely();
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            invokeOnFinishedSafely();
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 从会话历史中提取最近一条不为空的 {@link AssistantMessage} 文本。
     * 用于在流式运行结束后给前端返回一个干净的“最终答案”，避免用户只看到 “Step N: 工具X 返回” 这种调试信息。
     */
    protected String extractFinalAssistantText() {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message m = messageList.get(i);
            if (m instanceof AssistantMessage am) {
                String text = am.getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
