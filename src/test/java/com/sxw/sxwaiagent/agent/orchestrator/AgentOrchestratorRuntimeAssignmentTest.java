package com.sxw.sxwaiagent.agent.orchestrator;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.LegacyReActRuntime;
import com.sxw.sxwaiagent.agent.runtime.ToolUseLoopRuntime;
import com.sxw.sxwaiagent.conversation.ConversationContextService;
import com.sxw.sxwaiagent.conversation.ConversationEventService;
import com.sxw.sxwaiagent.memory.AgentRunSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorRuntimeAssignmentTest {

    @Test
    void conversationV3AssignsGovernedRuntimeToEveryProfile() {
        AgentOrchestrator orchestrator = orchestrator(true);

        assertThat(orchestrator.getRuntimeAssignments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                AgentProfileCode.GENERAL, "ToolUseLoopRuntime",
                AgentProfileCode.LOVE, "ToolUseLoopRuntime",
                AgentProfileCode.HERMES, "ToolUseLoopRuntime"));
    }

    @Test
    void rollbackModeKeepsLegacyProfilesAndGovernedGeneralRuntime() {
        AgentOrchestrator orchestrator = orchestrator(false);

        assertThat(orchestrator.getRuntimeAssignments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                AgentProfileCode.GENERAL, "ToolUseLoopRuntime",
                AgentProfileCode.LOVE, "LegacyReActRuntime",
                AgentProfileCode.HERMES, "LegacyReActRuntime"));
    }

    private AgentOrchestrator orchestrator(boolean conversationV3Enabled) {
        AgentProfile general = profile(AgentProfileCode.GENERAL);
        AgentProfile love = profile(AgentProfileCode.LOVE);
        AgentProfile hermes = profile(AgentProfileCode.HERMES);
        return new AgentOrchestrator(
                List.of(general, love, hermes),
                List.of(mock(LegacyReActRuntime.class), mock(ToolUseLoopRuntime.class)),
                mock(RequestGuard.class),
                mock(ConversationContextService.class),
                mock(ConversationEventService.class),
                mock(AgentRunSnapshotService.class),
                conversationV3Enabled);
    }

    private AgentProfile profile(AgentProfileCode code) {
        AgentProfile profile = mock(AgentProfile.class);
        when(profile.code()).thenReturn(code);
        return profile;
    }
}
