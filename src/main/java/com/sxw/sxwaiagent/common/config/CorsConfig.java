package com.sxw.sxwaiagent.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 全局跨域配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${sxw.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求
        registry.addMapping("/**")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
                // 生产环境必须在 application.yml 中配置 sxw.cors.allowed-origins
                .allowedOriginPatterns(
                        allowedOrigins != null && !allowedOrigins.isEmpty()
                                ? allowedOrigins.toArray(new String[0])
                                : new String[]{"http://localhost:*", "http://127.0.0.1:*"}
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
