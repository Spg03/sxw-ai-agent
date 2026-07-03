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

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   UserAccountRepository userAccountRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
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
}
