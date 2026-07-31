package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * <p>
 * 生产安全基线：
 * <ul>
 *   <li>认证端点（/auth/**、/health、/actuator/health、/actuator/info）保持公开</li>
 *   <li>所有业务 API 端点必须携带有效 JWT</li>
 *   <li>Actuator 敏感端点（prometheus、metrics 等）需要已认证用户（待实现角色管理后收紧为 ADMIN）</li>
 *   <li>Swagger/Knife4j 在生产环境通过 application-prod.yml 禁用</li>
 *   <li>CORS 通过 {@link com.sxw.sxwaiagent.common.config.CorsConfig} 统一配置，
 *       生产环境通过 {@code sxw.cors.allowed-origins} 指定允许的源</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /** 是否启用 Swagger/API 文档（生产环境通过 springdoc.api-docs.enabled=false 禁用） */
    @Value("${springdoc.api-docs.enabled:true}")
    private boolean swaggerEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   UserAccountRepository userAccountRepository,
                                                   TokenBlacklistService tokenBlacklistService) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenService, userAccountRepository, tokenBlacklistService);
        return http
                // 禁用 CSRF（无状态 API 不需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 集成 CORS 配置（CorsConfig），确保 preflight OPTIONS 请求通过安全层
                .cors(cors -> {})
                // 无状态会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 未认证请求返回 JSON 401，而非 Spring Security 默认的 403
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new RestAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> {
                        auth
                        // ─── 公开端点：无需认证 ───
                        // 登录/注册/刷新 token
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
                        // 应用健康检查
                        .requestMatchers("/health").permitAll()
                        // Actuator 健康检查 + 基本信息（K8s 探针、运维监控）
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Spring 默认错误页面
                        .requestMatchers("/error").permitAll()

                        // ─── Actuator 敏感端点：需要认证 ───
                        // TODO: 实现角色管理后改回 hasRole("ADMIN")，当前所有已认证用户可访问
                        .requestMatchers("/actuator/**").authenticated()

                        // ─── 前端 SPA 静态资源 ───
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/favicon.svg", "/assets/**", "/icons.svg").permitAll();

                        // Swagger/Knife4j API 文档：开发环境放行，生产环境拒绝
                        // 通过 springdoc.api-docs.enabled 控制（生产 application-prod.yml 中设为 false）
                        if (swaggerEnabled) {
                            auth.requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll();
                        } else {
                            auth.requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").denyAll();
                        }

                        // ─── 其余所有请求：需要 JWT 认证 ───
                        // 包括：/ai/**, /notes/**, /skills/**, /agents/**,
                        //       /api/agent/**, /api/traces/**, /api/dashboard/**,
                        //       /api/eval/**, /api/hermes/**, /api/plans/**,
                        //       /api/knowledge/**, /treeholes/**, /agent/traces/**,
                        //       /sse, /mcp/message, /auth/me, /auth/logout 等
                        auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
