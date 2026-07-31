package com.sxw.sxwaiagent.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Redis L2 缓存实现。仅存储 response text（不存完整 ChatClientResponse），
 * 对 L2 而言可接受——L1 缓存完整 response 对象。
 * <p>
 * Redis 不可用时 graceful degradation：catch 所有异常 + log warning + 返回 null / 静默跳过写入。
 */
public class RedisLlmResponseCache implements LlmResponseCache {

    private static final Logger log = LoggerFactory.getLogger(RedisLlmResponseCache.class);

    private static final String PREFIX = "llm:answer:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisLlmResponseCache(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public ChatClientResponse get(String key) {
        try {
            String text = redisTemplate.opsForValue().get(PREFIX + key);
            if (text == null || text.isBlank()) {
                return null;
            }
            return ChatClientResponse.builder()
                    .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                    .build();
        } catch (Exception e) {
            log.warn("Redis L2 cache GET failed for key={}, falling back: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, ChatClientResponse response) {
        try {
            String text = response.chatResponse().getResult().getOutput().getText();
            if (text != null && !text.isBlank()) {
                redisTemplate.opsForValue().set(PREFIX + key, text, ttl);
            }
        } catch (Exception e) {
            log.warn("Redis L2 cache PUT failed for key={}, skipping: {}", key, e.getMessage());
        }
    }

    /**
     * 删除指定 key 的缓存条目。
     */
    public void evict(String key) {
        try {
            redisTemplate.delete(PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis L2 cache EVICT failed for key={}, skipping: {}", key, e.getMessage());
        }
    }

    /**
     * 检查 Redis 连接是否可用。
     */
    public boolean isAvailable() {
        try {
            String result = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) connection ->
                            connection.ping());
            return "PONG".equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
