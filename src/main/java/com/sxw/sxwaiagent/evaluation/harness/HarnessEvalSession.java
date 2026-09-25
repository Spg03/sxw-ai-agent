package com.sxw.sxwaiagent.evaluation.harness;

public interface HarnessEvalSession extends AutoCloseable {
    HarnessEvalOutput execute(HarnessEvalInput input);

    @Override
    void close();
}
