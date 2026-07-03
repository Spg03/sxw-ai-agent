package com.sxw.sxwaiagent.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks X-SXW-API-Key when API-key protection is explicitly enabled.
 */
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-SXW-API-Key";

    private final ApiKeySecurityProperties properties;

    public ApiKeyInterceptor(ApiKeySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEffective()) {
            return true;
        }
        String provided = request.getHeader(HEADER_NAME);
        if (matches(provided, properties.getValue())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":40100,\"message\":\"missing or invalid API key\",\"data\":null}");
        return false;
    }

    private static boolean matches(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] left = provided.getBytes(StandardCharsets.UTF_8);
        byte[] right = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
