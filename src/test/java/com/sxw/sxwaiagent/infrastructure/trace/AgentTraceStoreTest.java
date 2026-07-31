package com.sxw.sxwaiagent.infrastructure.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentTraceStoreTest {

    @Test
    void storesRunsByChatIdAndReturnsMostRecentFirst() {
        AgentTraceProperties properties = new AgentTraceProperties();
        AgentTraceStore store = new AgentTraceStore(properties, new InMemoryAgentTraceRepository());

        String first = store.startRun("chat-a");
        String second = store.startRun("chat-a");
        store.startRun("chat-b");

        List<AgentTraceRun> runs = store.recentRuns("chat-a", 5);

        assertEquals(2, runs.size());
        assertEquals(second, runs.get(0).traceId());
        assertEquals(first, runs.get(1).traceId());
        assertNotEquals(runs.get(0).chatId(), "chat-b");
    }

    @Test
    void trimsRunsAndEventsByConfiguredLimits() {
        AgentTraceProperties properties = new AgentTraceProperties();
        properties.setMaxRuns(2);
        properties.setMaxEventsPerRun(2);
        AgentTraceStore store = new AgentTraceStore(properties, new InMemoryAgentTraceRepository());

        store.startRun("chat-a");
        String second = store.startRun("chat-a");
        String third = store.startRun("chat-a");

        store.appendEvent(third, 1, "think", null, "in-1", "out-1", "ok", 10);
        store.appendEvent(third, 2, "tool_call", "search", "in-2", "out-2", "ok", 20);
        store.appendEvent(third, 3, "finish", null, "", "done", "ok", 30);

        List<AgentTraceRun> runs = store.recentRuns("chat-a", 10);

        assertEquals(2, runs.size());
        assertEquals(third, runs.get(0).traceId());
        assertEquals(second, runs.get(1).traceId());
        assertEquals(2, runs.get(0).events().size());
        assertEquals("tool_call", runs.get(0).events().get(0).phase());
        assertEquals("finish", runs.get(0).events().get(1).phase());
    }

    @Test
    void finishRunIsIdempotent() {
        AgentTraceStore store = new AgentTraceStore(new AgentTraceProperties(), new InMemoryAgentTraceRepository());
        String traceId = store.startRun("chat-a");

        store.appendEvent(traceId, 1, "step", null, "", "done", "ok", 10);
        store.finishRun(traceId, "finished");
        store.finishRun(traceId, "finished");

        List<String> phases = store.recentRuns("chat-a", 1).get(0).events().stream()
                .map(AgentTraceEvent::phase)
                .toList();

        assertEquals(List.of("step", "finish"), phases);
    }
}
