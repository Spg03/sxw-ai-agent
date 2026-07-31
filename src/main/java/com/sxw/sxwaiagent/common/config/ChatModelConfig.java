package com.sxw.sxwaiagent.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
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
 */
@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    @Bean
    @Primary
    public ChatModel fallbackChatModel(
            @Qualifier("dashscopeChatModel") ChatModel primary,
            @Qualifier("ollamaChatModel") ChatModel fallback
    ) {
        log.info("FallbackChatModel initialized: primary=dashscope, fallback=ollama");
        return new FallbackChatModel(primary, fallback);
    }

    /**
     * 主备切换 ChatModel：优先使用 primary，失败时自动降级到 fallback。
     */
    static class FallbackChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

        private final ChatModel primary;
        private final ChatModel fallback;

        FallbackChatModel(ChatModel primary, ChatModel fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            try {
                return primary.call(prompt);
            } catch (Exception e) {
                log.warn("Primary ChatModel (dashscope) failed: {}, falling back to ollama", e.getMessage());
                return fallback.call(prompt);
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            try {
                return primary.stream(prompt)
                        .onErrorResume(e -> {
                            log.warn("Primary ChatModel (dashscope) stream failed: {}, falling back to ollama",
                                    e.getMessage());
                            return fallback.stream(prompt);
                        });
            } catch (Exception e) {
                log.warn("Primary ChatModel (dashscope) stream setup failed: {}, falling back to ollama",
                        e.getMessage());
                return fallback.stream(prompt);
            }
        }
    }
}
