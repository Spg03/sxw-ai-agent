package com.sxw.sxwaiagent.eval;

import java.time.Instant;

public record EvalResult(
    String resultId,
    String caseId,
    String agentType,
    String actualOutput,
    boolean passed,
    long latencyMs,
    String details,
    Instant createdAt
) {}
