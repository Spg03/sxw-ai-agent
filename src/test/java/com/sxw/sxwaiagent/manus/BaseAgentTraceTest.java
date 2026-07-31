package com.sxw.sxwaiagent.manus;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceProperties;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceRun;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import com.sxw.sxwaiagent.infrastructure.trace.InMemoryAgentTraceRepository;
import com.sxw.sxwaiagent.manus.model.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentTraceTest {

    static class OneStepAgent extends BaseAgent {
        @Override
        public String step() {
            setState(AgentState.FINISHED);
            return "done";
        }
    }

    @Test
    void runRecordsStartStepAndFinishEvents() {
        AgentTraceStore store = new AgentTraceStore(new AgentTraceProperties(), new InMemoryAgentTraceRepository());
        OneStepAgent agent = new OneStepAgent();
        agent.setName("test-agent");
        agent.enableTracing(store, "chat-1");

        agent.run("hello");

        List<AgentTraceRun> runs = store.recentRuns("chat-1", 1);
        assertEquals(1, runs.size());
        List<String> phases = runs.get(0).events().stream()
                .map(event -> event.phase())
                .toList();
        assertTrue(phases.contains("run_start"), phases.toString());
        assertTrue(phases.contains("step"), phases.toString());
        assertTrue(phases.contains("finish"), phases.toString());
        assertEquals("finished", runs.get(0).status());
    }
}
