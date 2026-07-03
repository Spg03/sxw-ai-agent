package com.sxw.sxwaiagent.infrastructure.cache;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

public class RedisLlmResponseCache implements LlmResponseCache {

    private static final String PREFIX = "llm:answer:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisLlmResponseCache(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public ChatClientResponse get(String key) {
        String text = redisTemplate.opsForValue().get(PREFIX + key);
        if (text == null || text.isBlank()) {
            return null;
        }
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                .build();
    }

    @Override
    public void put(String key, ChatClientResponse response) {
        String text = response.chatResponse().getResult().getOutput().getText();
        redisTemplate.opsForValue().set(PREFIX + key, text, ttl);
    }
}
