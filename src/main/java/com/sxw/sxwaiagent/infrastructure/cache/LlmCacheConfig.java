package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * LLM 缓存装配。仅当 {@code sxw.cache.llm.enabled=true} 时启用。
 * <p>
 * 双层缓存架构：L1(Caffeine) + L2(Redis)。
 * Redis 不可用时自动降级为纯 Caffeine L1。
 */
@Configuration
@EnableConfigurationProperties(LlmCacheProperties.class)
@ConditionalOnProperty(prefix = "sxw.cache.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmCacheConfig.class);

    @Bean
    public Cache<String, ChatClientResponse> llmAnswerCache(
            LlmCacheProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        Cache<String, ChatClientResponse> cache = Caffeine.newBuilder()
                .maximumSize(properties.getMaxSize())
                .expireAfterWrite(Duration.ofMinutes(properties.getTtlMinutes()))
                .recordStats()
                .build();
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            CaffeineCacheMetrics.monitor(registry, cache, "llmAnswerCache");
        }
        return cache;
    }

    /**
     * Redis L2 缓存 Bean。通过 {@code required = false} 注入 StringRedisTemplate，
     * Redis 不可用时返回 null，CompositeLlmResponseCache 自动降级为纯 L1。
     */
    @Bean
    public RedisLlmResponseCache redisLlmResponseCache(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            LlmCacheProperties properties) {
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate not available — Redis L2 cache disabled, using Caffeine L1 only");
            return null;
        }
        Duration ttl = Duration.ofMinutes(properties.getTtlMinutes());
        log.info("RedisLlmResponseCache initialized with TTL={}min", properties.getTtlMinutes());
        return new RedisLlmResponseCache(redisTemplate, ttl);
    }

    @Bean
    public LlmAnswerCacheAdvisor llmAnswerCacheAdvisor(
            Cache<String, ChatClientResponse> llmAnswerCache,
            @Autowired(required = false) RedisLlmResponseCache redisLlmResponseCache,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        CompositeLlmResponseCache composite = new CompositeLlmResponseCache(
                llmAnswerCache, redisLlmResponseCache);
        return new LlmAnswerCacheAdvisor(composite, meterRegistryProvider.getIfAvailable());
    }
}
