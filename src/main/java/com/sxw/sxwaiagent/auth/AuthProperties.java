package com.sxw.sxwaiagent.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.SecureRandom;
import java.util.Base64;

@ConfigurationProperties(prefix = "sxw.auth")
public class AuthProperties {

    private static final Logger log = LoggerFactory.getLogger(AuthProperties.class);
    private static final int SECRET_BYTE_LENGTH = 32; // 256 bits

    private String jwtSecret;

    private long jwtExpirationMinutes = 1440;

    public AuthProperties() {
        // 如果没有配置，生成随机密钥（仅适用于开发环境）
        // 生产环境必须在 application.yml 中配置 sxw.auth.jwt-secret
        this.jwtSecret = generateRandomSecret();
        log.warn("JWT secret not configured, using random secret. This is NOT safe for production! " +
                 "Set 'sxw.auth.jwt-secret' in application.yml");
    }

    private static String generateRandomSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

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
