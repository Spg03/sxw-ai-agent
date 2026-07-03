package com.sxw.sxwaiagent.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void protectedEndpointsAllowRequestsWhenApiKeyProtectionIsDisabled() throws Exception {
        mockMvc.perform(get("/agent/traces/chat-a"))
                .andExpect(status().isOk());
    }
}
