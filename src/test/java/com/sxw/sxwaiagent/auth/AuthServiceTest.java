package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.RefreshTokenRepository;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void registerCreatesUserWithHashedPasswordAndReturnsToken() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthProperties props = authProperties();
        JwtTokenService tokenService = tokenService(props);
        AuthService service = new AuthService(repository, refreshTokenRepository, encoder, tokenService, tokenBlacklistService, props);

        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(new RegisterRequest("alice", "password123", "Alice"));

        assertEquals("alice", response.user().username());
        assertEquals("Alice", response.user().nickname());
        assertTrue(tokenService.validateToken(response.token()));
        assertNotNull(response.refreshToken());
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getUsername().equals("alice")
                        && user.getPasswordHash() != null
                        && !user.getPasswordHash().equals("password123")
                        && user.getRole().equals("USER")
                        && user.isEnabled()
        ));
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
        AuthProperties props = authProperties();
        AuthService service = new AuthService(repository, refreshTokenRepository, new BCryptPasswordEncoder(), tokenService(props), tokenBlacklistService, props);
        when(repository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest("alice", "password123", "Alice")));
    }

    @Test
    void loginRejectsWrongPassword() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthProperties props = authProperties();
        AuthService service = new AuthService(repository, refreshTokenRepository, encoder, tokenService(props), tokenBlacklistService, props);
        UserAccount user = UserAccount.create("alice", encoder.encode("right-password"), "Alice");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> service.login(new LoginRequest("alice", "wrong-password")));
    }

    private static AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef");
        properties.setJwtExpirationMinutes(60);
        properties.setJwtRefreshExpirationMinutes(10080);
        return properties;
    }

    private static JwtTokenService tokenService(AuthProperties properties) {
        return new JwtTokenService(properties);
    }
}
