package com.sxw.sxwaiagent.infrastructure.cache;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LLM 缓存配置属性。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "sxw.cache.llm")
public class LlmCacheProperties {
    
    /**
     * 是否启用缓存。
     */
    private boolean enabled = true;
    
    /**
     * 缓存最大条目数。
     */
    @Min(value = 1, message = "maxSize must be at least 1")
    @Max(value = 100000, message = "maxSize must not exceed 100000")
    private long maxSize = 10000;
    
    /**
     * 缓存 TTL（分钟）。
     */
    @Min(value = 1, message = "ttlMinutes must be at least 1")
    @Max(value = 1440, message = "ttlMinutes must not exceed 1440")
    private int ttlMinutes = 60;
}
