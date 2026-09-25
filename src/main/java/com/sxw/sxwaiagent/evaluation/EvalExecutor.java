package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.evaluation.harness.DeterministicEvalToolset;
import com.sxw.sxwaiagent.evaluation.harness.EvalHarnessProperties;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalAdapter;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalAdapterRegistry;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalInput;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalOutput;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalSession;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import com.sxw.sxwaiagent.infrastructure.eval.CaseResult;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Evaluation executor with LLM-as-Judge integration.
 * <p>
 * Executes eval cases against the agent runtime, validates output using
 * keyword matching and/or LLM-as-Judge, and combines results per ValidationMode.
 */
@Service
public class EvalExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvalExecutor.class);
    private final HarnessEvalAdapterRegistry adapterRegistry;
    private final EvalHarnessProperties properties;
    private final ObjectProvider<LlmJudgeEvaluator> judgeProvider;

    public EvalExecutor(
        HarnessEvalAdapterRegistry adapterRegistry,
        EvalHarnessProperties properties,
        ObjectProvider<LlmJudgeEvaluator> judgeProvider
    ) {
        this.adapterRegistry = adapterRegistry;
        this.properties = properties;
        this.judgeProvider = judgeProvider;
    }

    public EvalResult execute(EvalCase evalCase) {
        return execute(evalCase, HarnessTargetCode.LOCAL);
    }

    public EvalResult execute(EvalCase evalCase, HarnessTargetCode target) {
        long startTime = System.currentTimeMillis();
        try {
            HarnessEvalAdapter adapter = adapterRegistry.require(target);
            String legacyRun = "legacy-" + UUID.randomUUID().toString().substring(0, 8);
            HarnessEvalOutput harnessOutput;
            try (HarnessEvalSession session = adapter.openRun(legacyRun)) {
                harnessOutput = session.execute(new HarnessEvalInput(legacyRun, evalCase.caseId(),
                    evalCase.profileCode(), evalCase.inputPrompt(),
                    DeterministicEvalToolset.NAMES.stream().sorted().toList(),
                    Duration.ofSeconds(properties.dsh().caseTimeoutSeconds()), 1));
            }
            if (!harnessOutput.successful()) {
                throw new IllegalStateException(harnessOutput.errorCategory() + ": " + harnessOutput.errorMessage());
            }
            String actualOutput = harnessOutput.answer();
            long durationMs = System.currentTimeMillis() - startTime;

            // Phase 1: Keyword validation
            boolean keywordPassed = evaluateKeyword(actualOutput, evalCase.expectedOutput(),
                evalCase.validationRules());

            // Phase 2: LLM Judge validation
            JudgeStatus judgeStatus = null;
            String judgeModel = null;
            Double judgeScore = null;
            String judgeReason = null;

            if (evalCase.judgeCriteria() != null && !evalCase.judgeCriteria().isBlank()) {
                LlmJudgeEvaluator judgeEvaluator = judgeProvider.getIfAvailable();
                if (judgeEvaluator == null) {
                    judgeStatus = JudgeStatus.UNAVAILABLE;
                    judgeReason = "LLM judge is not configured but judgeCriteria is set";
                } else {
                    try {
                        var infraCase = new com.sxw.sxwaiagent.infrastructure.eval.EvalCase(
                            evalCase.caseId(),
                            "eval",
                            evalCase.inputPrompt(),
                            List.of(),
                            List.of(),
                            evalCase.judgeCriteria(),
                            0
                        );
                        CaseResult.Check check = judgeEvaluator.evaluateCase(infraCase, actualOutput);
                        judgeScore = check.score();
                        judgeReason = check.reason();
                        judgeModel = "project-chatmodel";
                        judgeStatus = check.passed() ? JudgeStatus.PASSED : JudgeStatus.FAILED;
                    } catch (Exception e) {
                        judgeStatus = JudgeStatus.TIMEOUT;
                        judgeReason = "judge error: " + e.getMessage();
                        log.warn("Judge call failed for case {}: {}", evalCase.caseId(), e.getMessage());
                    }
                }
            }

            // Phase 3: Combine per ValidationMode
            ValidationMode mode = evalCase.validationMode() != null
                ? evalCase.validationMode() : ValidationMode.KEYWORD_ONLY;
            boolean passed = combineResults(mode, keywordPassed, judgeStatus);

            String validationDetails = null;
            if (!passed) {
                validationDetails = String.format("mode=%s, keyword=%s, judge=%s",
                    mode, keywordPassed, judgeStatus);
            }

            log.info("Eval case {}: passed={}, keyword={}, judge={} ({}ms)",
                evalCase.caseId(), passed, keywordPassed, judgeStatus, durationMs);

            return EvalResult.withJudge(evalCase.caseId(), evalCase.caseName(),
                passed, keywordPassed, actualOutput, evalCase.expectedOutput(),
                validationDetails, durationMs,
                judgeStatus, judgeModel, judgeScore, judgeReason, mode);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Eval case ERROR: {} ({}ms)", evalCase.caseId(), durationMs, e);
            return EvalResult.error(evalCase.caseId(), evalCase.caseName(), e.getMessage(), durationMs);
        }
    }

    public List<EvalResult> executeBatch(List<EvalCase> cases) {
        return executeBatch(cases, HarnessTargetCode.LOCAL);
    }

    public List<EvalResult> executeBatch(List<EvalCase> cases, HarnessTargetCode target) {
        List<EvalResult> results = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos();
        for (EvalCase evalCase : cases) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) {
                throw new IllegalStateException("评测运行超过 30 分钟或已中断");
            }
            if (!evalCase.canRun()) {
                log.warn("Skipping eval case: {} (status={})", evalCase.caseId(), evalCase.status());
                continue;
            }
            results.add(execute(evalCase, target));
        }
        return results;
    }

    private boolean evaluateKeyword(String actual, String expected, String rules) {
        if (actual == null || actual.isEmpty()) return false;
        if (expected == null || expected.isEmpty()) return true;
        if (rules == null || rules.isEmpty()) {
            return actual.contains(expected) || expected.contains(actual);
        }
        return actual.contains(expected);
    }

    private boolean combineResults(ValidationMode mode, boolean keywordPassed, JudgeStatus judgeStatus) {
        boolean judgePassed = judgeStatus == JudgeStatus.PASSED;
        return switch (mode) {
            case KEYWORD_ONLY -> keywordPassed;
            case LLM_ONLY -> judgePassed;
            case ALL -> keywordPassed && judgePassed;
            case ANY -> keywordPassed || judgePassed;
        };
    }
}
