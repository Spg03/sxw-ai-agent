package com.sxw.sxwaiagent.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void generatedTokenCanBeVerifiedAndResolvedToUsername() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef");
        properties.setJwtExpirationMinutes(60);
        JwtTokenService tokenService = new JwtTokenService(properties);

        String token = tokenService.generateToken("alice");

        assertTrue(tokenService.validateToken(token));
        assertEquals("alice", tokenService.resolveUsername(token));
    }
}
