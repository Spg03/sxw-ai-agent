package com.sxw.sxwaiagent.agent.prompt;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Single source of truth for the active prompt template version.
 * The numeric version is intentionally independent from the Tool-Use call number.
 */
@Component
public class PromptVersionCatalog {

    private final int activeVersion;

    public PromptVersionCatalog(@Value("${sxw.agent.prompt.active-version:2}") int activeVersion) {
        if (activeVersion < 1) throw new IllegalArgumentException("Prompt version must be positive");
        this.activeVersion = activeVersion;
    }

    public PromptIdentity identify(AgentProfile profile, AssembledPrompt prompt) {
        String profileCode = profile == null || profile.code() == null
                ? "UNKNOWN" : profile.code().name();
        return new PromptIdentity("AGENT_" + profileCode.toUpperCase(Locale.ROOT),
                activeVersion, prompt.staticHash());
    }
}
