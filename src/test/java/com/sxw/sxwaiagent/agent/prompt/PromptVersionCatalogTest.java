package com.sxw.sxwaiagent.agent.prompt;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptVersionCatalogTest {
    @Test
    void promptVersionIsIndependentFromToolLoopCallNumber() {
        AgentProfile profile = mock(AgentProfile.class);
        when(profile.code()).thenReturn(AgentProfileCode.GENERAL);
        AssembledPrompt prompt = new AssembledPrompt("rendered", List.of(), "static-hash", "dynamic", "all");

        PromptIdentity identity = new PromptVersionCatalog(7).identify(profile, prompt);

        assertEquals("AGENT_GENERAL", identity.code());
        assertEquals(7, identity.version());
        assertEquals("static-hash", identity.templateHash());
    }
}
