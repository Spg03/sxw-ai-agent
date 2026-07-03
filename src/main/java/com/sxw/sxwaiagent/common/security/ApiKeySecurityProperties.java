package com.sxw.sxwaiagent.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lightweight API key protection for demo/production deployments.
 */
@ConfigurationProperties(prefix = "sxw.security.api-key")
public class ApiKeySecurityProperties {

    private boolean enabled = false;

    private String value = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEffective() {
        return enabled && value != null && !value.isBlank();
    }
}
