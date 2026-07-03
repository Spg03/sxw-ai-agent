package com.sxw.sxwaiagent.infrastructure.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 缓存配置属性。
 */
@Data
@ConfigurationProperties(prefix = "sxw.cache.llm")
public class LlmCacheProperties {
    
    /**
     * 是否启用缓存。
     */
    private boolean enabled = true;
    
    /**
     * 缓存最大条目数。
     */
    private long maxSize = 10000;
    
    /**
     * 缓存 TTL（分钟）。
     */
    private int ttlMinutes = 60;
}
