package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceEvent;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceProperties;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceRun;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import com.sxw.sxwaiagent.infrastructure.trace.DbAgentTraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTraceControllerTest {

    @Test
    void returnsRecentRunsForChatId() throws Exception {
        String traceId = "test-trace-id";
        Instant now = Instant.now();
        AgentTraceEvent event = new AgentTraceEvent(
                traceId, "chat-1", 1, "think", "", "input", "output", "ok", 12, now);
        AgentTraceRun run = new AgentTraceRun(
                traceId, "chat-1", now, now, "finished", List.of(event));

        DbAgentTraceRepository repo = mock(DbAgentTraceRepository.class);
        when(repo.findRecentByChatId(eq("chat-1"), anyInt())).thenReturn(List.of(run));

        AgentTraceStore store = new AgentTraceStore(new AgentTraceProperties(), repo);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentTraceController(store)).build();

        mockMvc.perform(get("/api/agent/traces/chat-1").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].traceId").value(traceId))
                .andExpect(jsonPath("$.data[0].events[0].phase").value("think"));
    }
}
