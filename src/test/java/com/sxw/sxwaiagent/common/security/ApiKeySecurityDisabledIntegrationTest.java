package com.sxw.sxwaiagent.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Key 保护关闭时，公开端点仍可匿名访问；
 * 但业务端点仍需 JWT 认证（Spring Security 层）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sxw.security.api-key.enabled=false",
        "sxw.security.api-key.value="
})
class ApiKeySecurityDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void businessEndpointsRequireJwtAuthentication() throws Exception {
        // API Key 关闭后，业务端点仍需 JWT；无 token 应返回 401
        mockMvc.perform(get("/api/agent/traces/chat-a"))
                .andExpect(status().isUnauthorized());
    }
}
