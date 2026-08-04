package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.love.LoveApp;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import com.sxw.sxwaiagent.infrastructure.memory.ManusMemoryStore;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;

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

        AiController controller = new AiController(
                loveApp,
                new ToolCallback[0],
                mock(ChatModel.class),
                mock(Executor.class),
                mock(SkillRegistry.class),
                mock(ManusMemoryStore.class),
                mock(AgentTraceStore.class)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/ai/love_app/chat/ragflow/sync")
                        .param("message", "hello")
                        .param("chatId", "chat-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("ragflow answer"));

        verify(loveApp).doChatWithRagFlow("hello", "chat-1");
    }
}
