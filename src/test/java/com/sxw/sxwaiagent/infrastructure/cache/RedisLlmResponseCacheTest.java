package com.sxw.sxwaiagent.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLlmResponseCacheTest {

    @Test
    void storesAndRestoresAssistantTextByHashedKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("llm:answer:key-1")).thenReturn("cached answer");
        RedisLlmResponseCache cache = new RedisLlmResponseCache(redisTemplate, Duration.ofMinutes(15));

        cache.put("key-1", response("cached answer"));
        ChatClientResponse restored = cache.get("key-1");

        verify(values).set("llm:answer:key-1", "cached answer", Duration.ofMinutes(15));
        assertEquals("cached answer", restored.chatResponse().getResult().getOutput().getText());
    }

    private static ChatClientResponse response(String text) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                .build();
    }
}
