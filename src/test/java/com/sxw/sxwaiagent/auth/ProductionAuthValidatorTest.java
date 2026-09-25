package com.sxw.sxwaiagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionAuthValidatorTest {
    @Test void productionRejectsMissingOrShortSecret() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var validator = new ProductionAuthValidator(env);
        assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);
        env.setProperty("sxw.auth.jwt-secret", "short");
        assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);
        env.setProperty("sxw.auth.jwt-secret", "0123456789abcdef0123456789abcdef");
        assertDoesNotThrow(validator::afterSingletonsInstantiated);
    }
    @Test void developmentCanUseEphemeralSecret() {
        assertDoesNotThrow(new ProductionAuthValidator(new MockEnvironment())::afterSingletonsInstantiated);
    }
}
