package com.sxw.sxwaiagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sxw.auth")
public class AuthProperties {

    private String jwtSecret = "change-me-change-me-change-me-change-me";

    private long jwtExpirationMinutes = 1440;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationMinutes() {
        return jwtExpirationMinutes;
    }

    public void setJwtExpirationMinutes(long jwtExpirationMinutes) {
        this.jwtExpirationMinutes = jwtExpirationMinutes;
    }
}
