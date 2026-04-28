package com.sxw.sxwaiagent.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 应答缓存（Caffeine LRU + TTL）配置。
 * 配置前缀：{@code sxw.cache.llm}
 */
@ConfigurationProperties(prefix = "sxw.cache.llm")
public class LlmCacheProperties {

    /** 是否启用缓存。*/
    private boolean enabled = true;

    /** 最大条目数（超过则按 LRU 淘汰）。*/
    private long maxSize = 1000;

    /** 写后过期时间（分钟）。*/
    private long ttlMinutes = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
    public long getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
}
