package com.sxw.sxwaiagent.evaluation.harness;

import java.time.Duration;
import java.util.List;

public record HarnessEvalInput(
    String comparisonId,
    String caseId,
    String profileCode,
    String prompt,
    List<String> enabledTools,
    Duration timeout,
    int repeatIndex
) {
    public HarnessEvalInput {
        enabledTools = enabledTools == null ? List.of() : List.copyOf(enabledTools);
    }
}
