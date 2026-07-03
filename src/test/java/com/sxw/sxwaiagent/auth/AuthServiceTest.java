package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtTokenService tokenService = tokenService();
        AuthService service = new AuthService(repository, encoder, tokenService);

        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(new RegisterRequest("alice", "password123", "Alice"));

        assertEquals("alice", response.user().username());
        assertEquals("Alice", response.user().nickname());
        assertTrue(tokenService.validateToken(response.token()));
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getUsername().equals("alice")
                        && user.getPasswordHash() != null
                        && !user.getPasswordHash().equals("password123")
                        && user.getRole().equals("USER")
                        && user.isEnabled()
        ));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        AuthService service = new AuthService(repository, new BCryptPasswordEncoder(), tokenService());
        when(repository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest("alice", "password123", "Alice")));
    }

    @Test
    void loginRejectsWrongPassword() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthService service = new AuthService(repository, encoder, tokenService());
        UserAccount user = UserAccount.create("alice", encoder.encode("right-password"), "Alice");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> service.login(new LoginRequest("alice", "wrong-password")));
    }

    private static JwtTokenService tokenService() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef");
        properties.setJwtExpirationMinutes(60);
        return new JwtTokenService(properties);
    }
}
