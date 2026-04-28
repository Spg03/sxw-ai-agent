package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 缓存装配。仅当 {@code sxw.cache.llm.enabled=true} 时启用。
 */
@Configuration
@EnableConfigurationProperties(LlmCacheProperties.class)
@ConditionalOnProperty(prefix = "sxw.cache.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmCacheConfig {

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

    @Bean
    public LlmAnswerCacheAdvisor llmAnswerCacheAdvisor(
            Cache<String, ChatClientResponse> llmAnswerCache,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LlmAnswerCacheAdvisor(llmAnswerCache, meterRegistryProvider.getIfAvailable());
    }
}
