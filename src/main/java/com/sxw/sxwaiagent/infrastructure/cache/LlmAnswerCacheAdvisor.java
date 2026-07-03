package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * LLM 应答缓存 Advisor。
 * <p>
 * 把 prompt 文本（含已注入的会话历史 / RAG 上下文）SHA-256 后作为 key，
 * 命中时直接返回缓存的 {@link ChatClientResponse}，跳过 LLM 调用 → 秒级返回 + 降本。
 * <p>
 * 安全性约束：
 * <ul>
 *   <li>仅作用于非流式 {@code adviseCall}，流式不缓存（增量响应不可重放）；</li>
 *   <li>带 tool calls 的请求不缓存（agent 工具链是有状态的）；</li>
 *   <li>设为 {@link Ordered#LOWEST_PRECEDENCE} → 在 advisor 链中处于最内层，
 *       使 ChatMemory / Logger 等外层 advisor 仍能在缓存命中时执行其 post 逻辑
 *       （包括把响应写回会话记忆 / 输出延迟日志）。</li>
 * </ul>
 */
public class LlmAnswerCacheAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LlmAnswerCacheAdvisor.class);

    private final LlmResponseCache cache;
    private final MeterRegistry meterRegistry;

    public LlmAnswerCacheAdvisor(Cache<String, ChatClientResponse> cache,
                                 MeterRegistry meterRegistry) {
        this(new CaffeineLlmResponseCache(cache), meterRegistry);
    }

    public LlmAnswerCacheAdvisor(LlmResponseCache cache,
                                 MeterRegistry meterRegistry) {
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getName() {
        return "LlmAnswerCacheAdvisor";
    }

    @Override
    public int getOrder() {
        // 高 order = advisor 链内层；预留 100 让 Resilience 装饰器位于更内层（缓存命中跳过限流/熔断/重试）
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        Prompt prompt = chatClientRequest.prompt();
        if (!cacheable(prompt)) {
            return chain.nextCall(chatClientRequest);
        }
        String key = hashOf(prompt);
        ChatClientResponse cached = cache.get(key);
        if (cached != null) {
            log.info("llm-cache hit key={}", key);
            increment("hit");
            return cached;
        }
        ChatClientResponse response = chain.nextCall(chatClientRequest);
        if (isStorable(response)) {
            cache.put(key, response);
            increment("miss-stored");
        } else {
            increment("miss-skipped");
        }
        return response;
    }

    private boolean cacheable(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return false;
        }
        // 带 tools 的请求结果非确定，不缓存
        if (prompt.getOptions() != null) {
            try {
                java.lang.reflect.Method m = prompt.getOptions().getClass().getMethod("getToolCallbacks");
                Object tcs = m.invoke(prompt.getOptions());
                if (tcs instanceof java.util.Collection<?> c && !c.isEmpty()) {
                    return false;
                }
            } catch (ReflectiveOperationException ignored) {
                // 选项类型不支持 tool callbacks 反射访问 → 视为可缓存
            }
        }
        return true;
    }

    private boolean isStorable(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) return false;
        ChatResponse cr = response.chatResponse();
        if (cr.getResult() == null || cr.getResult().getOutput() == null) return false;
        AssistantMessage out = cr.getResult().getOutput();
        // 响应里若包含 tool calls 也不缓存（要等工具执行）
        if (out.getToolCalls() != null && !out.getToolCalls().isEmpty()) return false;
        String text = out.getText();
        return text != null && !text.isBlank();
    }

    private static String hashOf(Prompt prompt) {
        StringBuilder sb = new StringBuilder(1024);
        prompt.getInstructions().forEach(m -> sb.append(m.getMessageType()).append(':')
                .append(m.getText() == null ? "" : m.getText()).append('\n'));
        if (prompt.getOptions() != null) {
            sb.append("opts:").append(prompt.getOptions().getClass().getSimpleName());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // 极不可能发生；退化为 hashCode
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private void increment(String outcome) {
        if (meterRegistry == null) return;
        Counter.builder("ai.chat.cache")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
