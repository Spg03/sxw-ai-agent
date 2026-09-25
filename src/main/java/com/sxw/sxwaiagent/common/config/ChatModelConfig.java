package com.sxw.sxwaiagent.common.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

/**
 * ChatModel 主备切换配置。
 * <p>
 * dashscopeChatModel 为主模型，ollamaChatModel 为降级备选。
 * 注册一个 @Primary 的 FallbackChatModel，所有注入点自动获得降级能力。
 * Ollama 调用前检查 CircuitBreaker 状态，CB open 时直接走 fallback 逻辑。
 */
@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    @Bean
    @Primary
    public ChatModel fallbackChatModel(
            @Qualifier("dashscopeChatModel") ChatModel primary,
            @Qualifier("ollamaChatModel") ChatModel fallback,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        CircuitBreaker ollamaCb = circuitBreakerRegistry.circuitBreaker("ollama");
        log.info("FallbackChatModel initialized: primary=dashscope, fallback=ollama (CB protected)");
        return new FallbackChatModel(primary, fallback, ollamaCb);
    }

    /**
     * 主备切换 ChatModel：优先使用 primary，失败时自动降级到 fallback。
     * Ollama fallback 受 CircuitBreaker 保护：CB open 时跳过 ollama 直接返回错误。
     */
    static class FallbackChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

        private final ChatModel primary;
        private final ChatModel fallback;
        private final CircuitBreaker ollamaCb;

        FallbackChatModel(ChatModel primary, ChatModel fallback, CircuitBreaker ollamaCb) {
            this.primary = primary;
            this.fallback = fallback;
            this.ollamaCb = ollamaCb;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            try {
                return primary.call(dashScopePrompt(prompt));
            } catch (Exception e) {
                log.warn("Primary ChatModel (dashscope) failed: {}, falling back to ollama", e.getMessage());
                return callOllamaWithCb(prompt);
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            try {
                return primary.stream(dashScopePrompt(prompt))
                        .onErrorResume(e -> {
                            log.warn("Primary ChatModel (dashscope) stream failed: {}, falling back to ollama",
                                    e.getMessage());
                            return streamOllamaWithCb(prompt);
                        });
            } catch (Exception e) {
                log.warn("Primary ChatModel (dashscope) stream setup failed: {}, falling back to ollama",
                        e.getMessage());
                return streamOllamaWithCb(prompt);
            }
        }

        private ChatResponse callOllamaWithCb(Prompt prompt) {
            if (ollamaCb.getState() == CircuitBreaker.State.OPEN
                    || ollamaCb.getState() == CircuitBreaker.State.FORCED_OPEN) {
                log.warn("Ollama CircuitBreaker is OPEN, skipping fallback");
                throw new IllegalStateException("Ollama fallback unavailable (circuit breaker open)");
            }
            long start = System.nanoTime();
            try {
                ChatResponse response = fallback.call(ollamaPrompt(prompt));
                ollamaCb.onSuccess(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
                return response;
            } catch (Exception ex) {
                ollamaCb.onError(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, ex);
                throw ex;
            }
        }

        private Flux<ChatResponse> streamOllamaWithCb(Prompt prompt) {
            if (ollamaCb.getState() == CircuitBreaker.State.OPEN
                    || ollamaCb.getState() == CircuitBreaker.State.FORCED_OPEN) {
                log.warn("Ollama CircuitBreaker is OPEN, skipping fallback stream");
                return Flux.error(new IllegalStateException("Ollama fallback unavailable (circuit breaker open)"));
            }
            long start = System.nanoTime();
            return fallback.stream(ollamaPrompt(prompt))
                    .doOnComplete(() -> ollamaCb.onSuccess(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS))
                    .doOnError(ex -> ollamaCb.onError(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS, ex));
        }

        private Prompt dashScopePrompt(Prompt prompt) {
            ChatOptions source = prompt.getOptions();
            if (source == null || source instanceof DashScopeChatOptions) return prompt;
            // Alibaba 1.0.0.2 cannot deserialize generic ChatOptions.maxTokens
            // into its provider options. Use its native builder explicitly.
            var builder = DashScopeChatOptions.builder()
                    .withModel(source.getModel()).withTemperature(source.getTemperature())
                    .withMaxToken(source.getMaxTokens()).withTopP(source.getTopP())
                    .withTopK(source.getTopK());
            if (source.getStopSequences() != null) {
                builder.withStop(new java.util.ArrayList<Object>(source.getStopSequences()));
            }
            DashScopeChatOptions options = builder.build();
            copyToolOptions(source, options);
            return new Prompt(prompt.getInstructions(), options);
        }

        private Prompt ollamaPrompt(Prompt prompt) {
            ChatOptions source = prompt.getOptions();
            if (source == null) return prompt;
            // A DashScope model name (e.g. qwen-turbo) is not an Ollama model.
            // Leave model unset so the fallback uses its own configured default.
            OllamaOptions options = OllamaOptions.builder()
                    .temperature(source.getTemperature()).numPredict(source.getMaxTokens())
                    .topP(source.getTopP()).topK(source.getTopK())
                    .stop(source.getStopSequences()).presencePenalty(source.getPresencePenalty())
                    .frequencyPenalty(source.getFrequencyPenalty()).build();
            copyToolOptions(source, options);
            return new Prompt(prompt.getInstructions(), options);
        }

        private static void copyToolOptions(ChatOptions source, ToolCallingChatOptions target) {
            if (source instanceof ToolCallingChatOptions tools) {
                target.setToolCallbacks(tools.getToolCallbacks());
                target.setToolNames(tools.getToolNames());
                target.setToolContext(tools.getToolContext());
                target.setInternalToolExecutionEnabled(tools.getInternalToolExecutionEnabled());
            }
        }
    }
}
