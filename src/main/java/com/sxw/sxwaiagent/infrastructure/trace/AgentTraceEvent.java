package com.sxw.sxwaiagent.infrastructure.trace;

import java.time.Instant;

public record AgentTraceEvent(
        String traceId,
        String chatId,
        int step,
        String phase,
        String toolName,
        String inputSummary,
        String outputSummary,
        String status,
        long latencyMs,
        Instant createdAt
) {
}
