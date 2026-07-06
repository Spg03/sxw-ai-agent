package com.sxw.sxwaiagent.eval;

import java.time.Instant;

public record EvalCase(
    String caseId,
    String name,
    String input,
    String expectedOutput,
    String tags,
    Instant createdAt
) {}
