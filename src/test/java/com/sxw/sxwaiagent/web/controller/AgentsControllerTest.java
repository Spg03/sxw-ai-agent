package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.classic.ClassicAgentService;
import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentsControllerTest {

    @Test
    void modesReturnsClassicAndHermes() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentsController(mock(ClassicAgentService.class), mock(HermesAgent.class))
        ).build();

        mockMvc.perform(get("/api/agents/modes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("classic"))
                .andExpect(jsonPath("$.data[1].id").value("hermes"));
    }

    @Test
    void classicChatDelegatesToClassicAgentService() throws Exception {
        ClassicAgentService classicAgentService = mock(ClassicAgentService.class);
        when(classicAgentService.chat("hello", "chat-1", "ragflow")).thenReturn("answer");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentsController(classicAgentService, mock(HermesAgent.class))
        ).build();

        mockMvc.perform(get("/api/agents/classic/chat")
                        .param("message", "hello")
                        .param("chatId", "chat-1")
                        .param("mode", "ragflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("answer"));
    }

    @Test
    void hermesChatReturnsStructuredReply() throws Exception {
        HermesAgent hermesAgent = mock(HermesAgent.class);
        when(hermesAgent.comfort("今天很累", "chat-1"))
                .thenReturn(new HermesReply("我在听。", "tired", "你需要休息。", "先喝点水。"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentsController(mock(ClassicAgentService.class), hermesAgent)
        ).build();

        mockMvc.perform(get("/api/agents/hermes/chat")
                        .param("message", "今天很累")
                        .param("chatId", "chat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("我在听。"))
                .andExpect(jsonPath("$.data.emotionTag").value("tired"));
    }
}
