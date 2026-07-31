package com.sxw.sxwaiagent.common.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Lightweight API key protection for demo/production deployments.
 */
@Validated
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

    @AssertTrue(message = "API key value must not be blank when api-key security is enabled")
    @JsonIgnore
    public boolean isValidApiKey() {
        return !enabled || (value != null && !value.isBlank());
    }

    public boolean isEffective() {
        return enabled && value != null && !value.isBlank();
    }
}
