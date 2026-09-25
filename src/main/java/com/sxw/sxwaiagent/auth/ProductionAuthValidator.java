package com.sxw.sxwaiagent.auth;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Validate after configuration binding, before accepting requests. */
@Component
public class ProductionAuthValidator implements SmartInitializingSingleton {
    private final Environment environment;

    public ProductionAuthValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) return;
        String secret = environment.getProperty("sxw.auth.jwt-secret");
        if (secret == null || secret.isBlank() || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Production requires sxw.auth.jwt-secret (SXW_AUTH_JWT_SECRET), at least 32 bytes");
        }
    }
}
