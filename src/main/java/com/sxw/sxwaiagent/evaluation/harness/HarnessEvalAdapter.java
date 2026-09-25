package com.sxw.sxwaiagent.evaluation.harness;

public interface HarnessEvalAdapter {
    HarnessTargetCode targetCode();

    String targetVersion();

    String model();

    String configHash();

    HarnessCapabilities capabilities();

    HarnessEvalSession openRun(String comparisonId);
}
