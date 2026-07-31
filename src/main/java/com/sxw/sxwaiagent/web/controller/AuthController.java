package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthService;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.auth.dto.AuthResponse;
import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RefreshRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.dto.UserView;
import com.sxw.sxwaiagent.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refreshToken(request.refreshToken()));
    }

    @GetMapping("/me")
    public Result<UserView> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.ok(authService.me(user.username()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication, HttpServletRequest request) {
        String accessToken = extractBearerToken(request);
        String username = ((AuthenticatedUser) authentication.getPrincipal()).username();
        authService.logout(accessToken, username);
        return Result.ok();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }
}
