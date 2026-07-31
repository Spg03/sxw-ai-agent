package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthService;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.auth.dto.AuthResponse;
import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RefreshRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.dto.UserView;
import com.sxw.sxwaiagent.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证管理", description = "用户注册、登录、令牌刷新与登出")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户注册", description = "创建新用户账号，返回访问令牌和刷新令牌")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "用户登录", description = "验证凭据，返回 JWT 访问令牌和刷新令牌")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "刷新令牌", description = "使用刷新令牌获取新的访问令牌")
    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refreshToken(request.refreshToken()));
    }

    @Operation(summary = "获取当前用户信息", description = "返回当前已认证用户的详细信息")
    @GetMapping("/me")
    public Result<UserView> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.ok(authService.me(user.username()));
    }

    @Operation(summary = "用户登出", description = "使当前访问令牌失效并加入黑名单")
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
