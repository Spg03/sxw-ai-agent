package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.dto.AuthResponse;
import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.dto.UserView;
import com.sxw.sxwaiagent.auth.model.RefreshToken;
import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.RefreshTokenRepository;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthProperties authProperties;

    public AuthService(UserAccountRepository userAccountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       TokenBlacklistService tokenBlacklistService,
                       AuthProperties authProperties) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userAccountRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists");
        }
        UserAccount user = UserAccount.create(username, passwordEncoder.encode(request.password()), request.nickname());
        UserAccount saved = userAccountRepository.save(user);
        return buildAuthResponse(saved.getUsername(), UserView.from(saved));
    }

    // 登录成功后会创建并保存 refresh token，因此必须使用可写事务。
    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        return buildAuthResponse(user.getUsername(), UserView.from(user));
    }

    /**
     * 使用 refresh token 换取新的 access token + refresh token 对。
     * 旧的 refresh token 将被 revoke。
     */
    @Transactional
    public AuthResponse refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("refresh token is required");
        }
        String hash = JwtTokenService.getTokenHash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("invalid refresh token"));

        if (!stored.isActive()) {
            throw new IllegalArgumentException("refresh token is revoked or expired");
        }

        // revoke 旧 refresh token
        refreshTokenRepository.revokeByTokenHash(hash);

        // 生成新的 token 对
        return buildAuthResponse(stored.username(), null);
    }

    /**
     * 注销：将 access token 加入黑名单，并 revoke 该用户所有 refresh token。
     *
     * @param accessToken 当前请求携带的 JWT access token
     * @param username    当前已认证用户名
     */
    @Transactional
    public void logout(String accessToken, String username) {
        // 将 access token 加入黑名单（以便在 JWT 过期前主动失效）
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                long expiresAtMs = jwtTokenService.resolveExpiresAtMs(accessToken);
                tokenBlacklistService.addToBlacklist(accessToken, expiresAtMs);
            } catch (RuntimeException e) {
                log.warn("Failed to blacklist access token for user {}: {}", username, e.getMessage());
            }
        }
        // revoke 该用户所有 refresh token
        int revoked = refreshTokenRepository.revokeByUsername(username);
        log.info("User {} logged out, revoked {} refresh token(s)", username, revoked);
    }

    @Transactional(readOnly = true)
    public UserView me(String username) {
        return userAccountRepository.findByUsername(username)
                .map(UserView::from)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    /**
     * 生成 accessToken + refreshToken 对，并将 refreshToken 持久化到 DB。
     */
    private AuthResponse buildAuthResponse(String username, UserView userView) {
        String accessToken = jwtTokenService.generateToken(username);
        String rawRefreshToken = jwtTokenService.generateRefreshToken();
        String refreshHash = JwtTokenService.getTokenHash(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(authProperties.getJwtRefreshExpirationMinutes() * 60);
        refreshTokenRepository.save(RefreshToken.create(refreshHash, username, expiresAt));
        return new AuthResponse(accessToken, rawRefreshToken, userView);
    }
}
