package com.sxw.sxwaiagent.evaluation.harness;

import java.util.Set;

public record HarnessCapabilities(
    boolean available,
    boolean toolEvents,
    boolean tokenUsage,
    Set<String> deterministicTools,
    String unavailableReason
) {
    public static HarnessCapabilities unavailable(String reason) {
        return new HarnessCapabilities(false, false, false, Set.of(), reason);
    }
}
