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
 * API Key 保护开启时的集成测试。
 * <p>
 * 注意：Spring Security JWT 认证优先于 API Key 拦截器（Filter 在 Interceptor 之前执行）。
 * 本测试通过 {@code addFilters = false} 禁用 Security Filter，
 * 单独验证 API Key 拦截器（MVC Interceptor 层）的行为。
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "sxw.security.api-key.enabled=true",
        "sxw.security.api-key.value=test-secret"
})
class ApiKeySecurityEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointsRejectRequestsWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/agent/traces/chat-a"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectRequestsWithWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/agent/traces/chat-a")
                        .header(ApiKeyInterceptor.HEADER_NAME, "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsAllowRequestsWithCorrectApiKey() throws Exception {
        mockMvc.perform(get("/api/agent/traces/chat-a")
                        .header(ApiKeyInterceptor.HEADER_NAME, "test-secret"))
                .andExpect(status().isOk());
    }
}
