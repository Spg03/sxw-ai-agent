package com.sxw.sxwaiagent.infrastructure.trace;

import java.time.Instant;
import java.util.List;

public record AgentTraceRun(
        String traceId,
        String chatId,
        Instant startedAt,
        Instant finishedAt,
        String status,
        List<AgentTraceEvent> events
) {
}
