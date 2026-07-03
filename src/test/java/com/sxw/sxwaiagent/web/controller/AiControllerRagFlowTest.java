package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.love.LoveApp;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerRagFlowTest {

    @Test
    void loveAppRagFlowEndpointDelegatesToLoveApp() throws Exception {
        LoveApp loveApp = mock(LoveApp.class);
        when(loveApp.doChatWithRagFlow("hello", "chat-1")).thenReturn("ragflow answer");

        AiController controller = new AiController();
        ReflectionTestUtils.setField(controller, "loveApp", loveApp);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/ai/love_app/chat/ragflow/sync")
                        .param("message", "hello")
                        .param("chatId", "chat-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("ragflow answer"));

        verify(loveApp).doChatWithRagFlow("hello", "chat-1");
    }
}
