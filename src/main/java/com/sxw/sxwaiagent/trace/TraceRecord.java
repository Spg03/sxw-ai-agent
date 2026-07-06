package com.sxw.sxwaiagent.trace;

import java.time.Instant;

public record TraceRecord(
    String traceId,
    String agentType,
    String input,
    String output,
    String toolCalls,
    long latencyMs,
    Instant createdAt
) {}
