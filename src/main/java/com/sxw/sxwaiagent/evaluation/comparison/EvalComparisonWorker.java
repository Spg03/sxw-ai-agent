package com.sxw.sxwaiagent.evaluation.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.evaluation.*;
import com.sxw.sxwaiagent.evaluation.harness.*;
import com.sxw.sxwaiagent.infrastructure.eval.CaseResult;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class EvalComparisonWorker {
    private static final Logger log = LoggerFactory.getLogger(EvalComparisonWorker.class);
    private final EvalComparisonRepository repository;
    private final EvalCaseRepository caseRepository;
    private final HarnessEvalAdapterRegistry adapters;
    private final EvalEventLogStore eventLogStore;
    private final ObjectProvider<LlmJudgeEvaluator> judgeProvider;
    private final EvalHarnessProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean queueUnavailableLogged = new AtomicBoolean();

    public EvalComparisonWorker(EvalComparisonRepository repository, EvalCaseRepository caseRepository,
                                HarnessEvalAdapterRegistry adapters, EvalEventLogStore eventLogStore,
                                ObjectProvider<LlmJudgeEvaluator> judgeProvider,
                                EvalHarnessProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.adapters = adapters;
        this.eventLogStore = eventLogStore;
        this.judgeProvider = judgeProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${sxw.eval.worker-delay-ms:2000}")
    public void poll() {
        try {
            repository.claimNext().ifPresent(this::executeSafely);
            queueUnavailableLogged.set(false);
        } catch (DataAccessException e) {
            // Flyway can be disabled in slice/context tests. The queue becomes available
            // automatically after the comparison migration has been applied.
            if (queueUnavailableLogged.compareAndSet(false, true)) {
                log.debug("Eval comparison queue is not ready: {}", e.getMostSpecificCause().getMessage());
            }
        }
    }

    private void executeSafely(EvalComparison comparison) {
        try {
            execute(comparison);
            repository.complete(comparison.comparisonId());
        } catch (Exception e) {
            log.error("Comparison {} failed", comparison.comparisonId(), e);
            repository.retryOrFail(comparison.comparisonId(), comparison.attemptCount(), e.getMessage());
        }
    }

    private void execute(EvalComparison comparison) {
        List<EvalCase> cases = caseRepository.findByIds(comparison.caseIds());
        long runDeadline = System.currentTimeMillis() + Duration.ofMinutes(properties.dsh().runTimeoutMinutes()).toMillis();
        for (HarnessTargetCode target : comparison.targets()) {
            HarnessEvalAdapter adapter = adapters.require(target);
            try (HarnessEvalSession session = adapter.openRun(comparison.comparisonId())) {
                for (EvalCase evalCase : cases) {
                    if (!evalCase.canRun()) continue;
                    for (int repeat = 1; repeat <= comparison.repeats(); repeat++) {
                        if (System.currentTimeMillis() > runDeadline) {
                            saveError(comparison, evalCase, adapter, repeat, "RUN_TIMEOUT", "Comparison run timeout");
                            continue;
                        }
                        HarnessEvalInput input = new HarnessEvalInput(comparison.comparisonId(), evalCase.caseId(),
                            evalCase.profileCode(), evalCase.inputPrompt(),
                            DeterministicEvalToolset.NAMES.stream().sorted().toList(),
                            Duration.ofSeconds(properties.dsh().caseTimeoutSeconds()), repeat);
                        HarnessEvalOutput output = session.execute(input);
                        save(comparison, evalCase, adapter, repeat, output);
                    }
                }
            } catch (Exception targetError) {
                log.warn("Target {} could not run comparison {}: {}", target, comparison.comparisonId(), targetError.getMessage());
                for (EvalCase evalCase : cases) {
                    for (int repeat = 1; repeat <= comparison.repeats(); repeat++) {
                        saveError(comparison, evalCase, adapter, repeat, "TARGET_STARTUP", targetError.getMessage());
                    }
                }
            }
        }
    }

    private void save(EvalComparison comparison, EvalCase evalCase, HarnessEvalAdapter adapter,
                      int repeat, HarnessEvalOutput output) {
        boolean keywordPassed = output.successful() && keyword(output.answer(), evalCase.expectedOutput(), evalCase.validationRules());
        JudgeEvaluation judge = output.successful() ? judge(evalCase, output.answer()) : JudgeEvaluation.none();
        boolean passed = output.successful() && combine(evalCase.validationMode(), keywordPassed, judge.status());
        String objectKey = eventLogStore.store(comparison.comparisonId(), evalCase.caseId(),
            adapter.targetCode().name(), repeat, output.events());
        String metrics;
        try { metrics = objectMapper.writeValueAsString(java.util.Map.of("eventCount", output.events().size())); }
        catch (Exception e) { metrics = "{}"; }
        repository.saveResult(new EvalTargetResult(comparison.comparisonId(), evalCase.caseId(),
            evalCase.caseName(), adapter.targetCode(), adapter.targetVersion(), adapter.model(), adapter.configHash(),
            repeat, passed, keywordPassed, judge.status(), judge.score(), judge.reason(), output.answer(),
            evalCase.expectedOutput(), output.stopReason(), output.inputTokens(), output.outputTokens(),
            output.latencyMs(), output.toolCallCount(), output.toolSuccessRate(), output.securityViolationCount(),
            objectKey, output.errorCategory(), output.errorMessage(), metrics));
    }

    private void saveError(EvalComparison comparison, EvalCase evalCase, HarnessEvalAdapter adapter,
                           int repeat, String category, String message) {
        save(comparison, evalCase, adapter, repeat,
            HarnessEvalOutput.error(category, message, 0, List.of()));
    }

    private JudgeEvaluation judge(EvalCase evalCase, String output) {
        if (evalCase.judgeCriteria() == null || evalCase.judgeCriteria().isBlank()) return JudgeEvaluation.none();
        LlmJudgeEvaluator evaluator = judgeProvider.getIfAvailable();
        if (evaluator == null) return new JudgeEvaluation(JudgeStatus.UNAVAILABLE, null, "Judge unavailable");
        try {
            var judgeCase = new com.sxw.sxwaiagent.infrastructure.eval.EvalCase(evalCase.caseId(), "eval",
                evalCase.inputPrompt(), List.of(), List.of(), evalCase.judgeCriteria(), 0);
            CaseResult.Check check = evaluator.evaluateCase(judgeCase, output);
            return new JudgeEvaluation(check.passed() ? JudgeStatus.PASSED : JudgeStatus.FAILED,
                check.score(), check.reason());
        } catch (Exception e) {
            return new JudgeEvaluation(JudgeStatus.TIMEOUT, null, "Judge error: " + e.getMessage());
        }
    }

    private boolean keyword(String actual, String expected, String rules) {
        if (actual == null || actual.isBlank()) return false;
        if (expected == null || expected.isBlank()) return true;
        return actual.contains(expected) || (rules == null && expected.contains(actual));
    }

    private boolean combine(ValidationMode configured, boolean keyword, JudgeStatus judge) {
        ValidationMode mode = configured == null ? ValidationMode.KEYWORD_ONLY : configured;
        boolean judgePassed = judge == JudgeStatus.PASSED;
        return switch (mode) {
            case KEYWORD_ONLY -> keyword;
            case LLM_ONLY -> judgePassed;
            case ALL -> keyword && judgePassed;
            case ANY -> keyword || judgePassed;
        };
    }

    private record JudgeEvaluation(JudgeStatus status, Double score, String reason) {
        private static JudgeEvaluation none() { return new JudgeEvaluation(null, null, null); }
    }
}
