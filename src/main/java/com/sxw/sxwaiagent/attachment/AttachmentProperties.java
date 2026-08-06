package com.sxw.sxwaiagent.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sxw.agent.attachment")
public record AttachmentProperties(boolean enabled, String endpoint, String accessKey, String secretKey, String bucket, long maxBytes) {
    public AttachmentProperties { if (maxBytes <= 0) maxBytes = 10 * 1024 * 1024; }
}
