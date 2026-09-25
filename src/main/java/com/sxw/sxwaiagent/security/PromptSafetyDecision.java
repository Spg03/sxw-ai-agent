package com.sxw.sxwaiagent.security;

import java.util.List;
import java.util.Map;

public record PromptSafetyDecision(PromptRiskLevel riskLevel, boolean sanitized, boolean blocked,
                                   boolean blockDangerousTools, List<String> reasons, String userMessage,
                                   String eventId) {
    public static PromptSafetyDecision safe() { return new PromptSafetyDecision(PromptRiskLevel.LOW, false, false, false, List.of(), null, null); }
    public Map<String, Object> publicView() { return Map.of("riskLevel", riskLevel.name(), "sanitized", sanitized, "blocked", blocked, "message", userMessage == null ? "" : userMessage); }
}
