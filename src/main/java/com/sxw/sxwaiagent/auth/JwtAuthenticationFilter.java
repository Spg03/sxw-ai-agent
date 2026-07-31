package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserAccountRepository userAccountRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   UserAccountRepository userAccountRepository,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountRepository = userAccountRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            // 黑名单检查：已 logout 的 token 必须拒绝
            if (tokenBlacklistService.isBlacklisted(token)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (jwtTokenService.validateToken(token)) {
                String username = jwtTokenService.resolveUsername(token);
                userAccountRepository.findByUsername(username)
                        .filter(UserAccount::isEnabled)
                        .map(AuthenticatedUser::from)
                        .ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                )
                        ));
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 提取 JWT token：优先从 Authorization Header 读取，
     * 对于 SSE 端点（EventSource 不支持自定义 Header）允许从 query parameter 读取。
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 Authorization Header 读取
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        // 2. SSE 端点允许从 query parameter 读取 token（最小化安全影响）
        String path = request.getRequestURI();
        if (path != null && path.endsWith("/stream")) {
            String token = request.getParameter("token");
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        return null;
    }
}
