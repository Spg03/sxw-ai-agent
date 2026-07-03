package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceProperties;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTraceControllerTest {

    @Test
    void returnsRecentRunsForChatId() throws Exception {
        AgentTraceStore store = new AgentTraceStore(new AgentTraceProperties());
        String traceId = store.startRun("chat-1");
        store.appendEvent(traceId, 1, "think", null, "input", "output", "ok", 12);
        store.finishRun(traceId, "finished");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentTraceController(store)).build();

        mockMvc.perform(get("/agent/traces/chat-1").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].traceId").value(traceId))
                .andExpect(jsonPath("$.data[0].events[0].phase").value("think"));
    }
}
