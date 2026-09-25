package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record HarnessEvalOutput(
    String answer,
    String stopReason,
    Integer inputTokens,
    Integer outputTokens,
    long latencyMs,
    int toolCallCount,
    double toolSuccessRate,
    int securityViolationCount,
    List<JsonNode> events,
    String errorCategory,
    String errorMessage
) {
    public HarnessEvalOutput {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public boolean successful() {
        return errorCategory == null;
    }

    public static HarnessEvalOutput error(String category, String message, long latencyMs, List<JsonNode> events) {
        return new HarnessEvalOutput(null, "error", null, null, latencyMs, 0, 0.0, 0,
            events, category, message);
    }
}
