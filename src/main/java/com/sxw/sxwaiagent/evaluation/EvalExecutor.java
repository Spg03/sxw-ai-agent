package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import com.sxw.sxwaiagent.infrastructure.eval.CaseResult;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final double JUDGE_PASS_THRESHOLD = 0.7;

    private final List<AgentProfile> profileList;
    private final List<AgentRuntime> runtimeList;
    private final ObjectProvider<LlmJudgeEvaluator> judgeProvider;

    private Map<AgentProfileCode, AgentProfile> profileMap;
    private AgentRuntime runtime;

    public EvalExecutor(
        List<AgentProfile> profileList,
        List<AgentRuntime> runtimeList,
        ObjectProvider<LlmJudgeEvaluator> judgeProvider
    ) {
        this.profileList = profileList;
        this.runtimeList = runtimeList;
        this.judgeProvider = judgeProvider;
    }

    @PostConstruct
    void init() {
        profileMap = new HashMap<>();
        for (AgentProfile p : profileList) {
            profileMap.put(p.code(), p);
        }
        runtime = runtimeList.stream()
            .filter(r -> r.getClass().getSimpleName().equals("ToolUseLoopRuntime"))
            .findFirst()
            .orElse(runtimeList.isEmpty() ? null : runtimeList.get(0));
        log.info("EvalExecutor initialized: {} profiles, runtime={}",
            profileMap.size(), runtime != null ? runtime.getClass().getSimpleName() : "NONE");
    }

    public EvalResult execute(EvalCase evalCase) {
        long startTime = System.currentTimeMillis();
        try {
            AgentProfile profile = profileMap.get(AgentProfileCode.valueOf(evalCase.profileCode()));
            if (profile == null) {
                throw new IllegalArgumentException("Profile not found: " + evalCase.profileCode());
            }
            if (runtime == null) {
                throw new IllegalStateException("No runtime configured");
            }

            String requestId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
            AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId("trace-" + requestId)
                .chatId("eval-chat-" + evalCase.caseId())
                .profile(profile)
                .userMessage(evalCase.inputPrompt())
                .history(List.of())
                .metadata(Map.of("evalCaseId", evalCase.caseId()))
                .build();

            AgentResponse response = runtime.execute(context);
            String actualOutput = response.answer();
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
        List<EvalResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            if (!evalCase.canRun()) {
                log.warn("Skipping eval case: {} (status={})", evalCase.caseId(), evalCase.status());
                continue;
            }
            results.add(execute(evalCase));
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
