package com.sxw.sxwaiagent.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ApiKeySecurityProperties.class)
public class ApiKeySecurityConfig implements WebMvcConfigurer {

    private final ApiKeySecurityProperties properties;

    public ApiKeySecurityConfig(ApiKeySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyInterceptor(properties))
                .addPathPatterns("/api/ai/**", "/api/notes/**", "/api/skills/**", "/api/agent/**");
    }
}
