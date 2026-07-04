package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评测执行器
 * <p>
 * 执行评测用例，验证 Agent 输出是否符合预期。
 */
@Service
public class EvalExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvalExecutor.class);

    private final Map<AgentProfileCode, AgentProfile> profileMap;
    private final Map<AgentProfileCode, AgentRuntime> runtimeMap;

    public EvalExecutor(
        Map<AgentProfileCode, AgentProfile> profileMap,
        Map<AgentProfileCode, AgentRuntime> runtimeMap
    ) {
        this.profileMap = profileMap;
        this.runtimeMap = runtimeMap;
    }

    /**
     * 执行单个评测用例
     */
    public EvalResult execute(EvalCase evalCase) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Executing eval case: {} - {}", evalCase.caseId(), evalCase.caseName());

            // 获取 Profile
            AgentProfile profile = profileMap.get(evalCase.profileCode());
            if (profile == null) {
                throw new IllegalArgumentException("Profile not found: " + evalCase.profileCode());
            }

            // 获取 Runtime
            AgentRuntime runtime = runtimeMap.get(evalCase.profileCode());
            if (runtime == null) {
                throw new IllegalStateException("No runtime configured for profile: " + evalCase.profileCode());
            }

            // 构建 AgentContext
            String requestId = "eval-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
            
            AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .chatId("eval-chat-" + evalCase.caseId())
                .profile(profile)
                .userMessage(evalCase.inputPrompt())
                .history(List.of())
                .metadata(Map.of("evalCaseId", evalCase.caseId()))
                .build();

            // 调用 Agent 执行
            AgentResponse response = runtime.execute(context);
            String actualOutput = response.answer();

            long durationMs = System.currentTimeMillis() - startTime;

            // 验证输出
            boolean passed = validateOutput(actualOutput, evalCase.expectedOutput(), evalCase.validationRules());

            if (passed) {
                log.info("Eval case PASSED: {} ({}ms)", evalCase.caseId(), durationMs);
                return EvalResult.pass(evalCase.caseId(), evalCase.caseName(), actualOutput, evalCase.expectedOutput(), durationMs);
            } else {
                String validationDetails = buildValidationDetails(actualOutput, evalCase.expectedOutput());
                log.warn("Eval case FAILED: {} ({}ms) - {}", evalCase.caseId(), durationMs, validationDetails);
                return EvalResult.fail(evalCase.caseId(), evalCase.caseName(), actualOutput, evalCase.expectedOutput(), validationDetails, durationMs);
            }

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Eval case ERROR: {} ({}ms) - {}", evalCase.caseId(), durationMs, e.getMessage(), e);
            return EvalResult.error(evalCase.caseId(), evalCase.caseName(), e.getMessage(), durationMs);
        }
    }

    /**
     * 批量执行评测用例
     */
    public List<EvalResult> executeBatch(List<EvalCase> cases) {
        List<EvalResult> results = new ArrayList<>();
        
        for (EvalCase evalCase : cases) {
            if (!evalCase.canRun()) {
                log.warn("Skipping eval case: {} (status={})", evalCase.caseId(), evalCase.status());
                continue;
            }
            
            EvalResult result = execute(evalCase);
            results.add(result);
        }
        
        return results;
    }

    /**
     * 验证输出是否符合预期
     */
    private boolean validateOutput(String actualOutput, String expectedOutput, String validationRules) {
        if (actualOutput == null || actualOutput.isEmpty()) {
            return false;
        }

        if (expectedOutput == null || expectedOutput.isEmpty()) {
            // 没有期望输出，只要不为空就算通过
            return true;
        }

        // 简单验证：包含关键字
        if (validationRules == null || validationRules.isEmpty()) {
            return actualOutput.contains(expectedOutput) || expectedOutput.contains(actualOutput);
        }

        // 根据验证规则执行
        // TODO: 实现更复杂的验证逻辑（正则、语义相似度等）
        return actualOutput.contains(expectedOutput);
    }

    /**
     * 构建验证详情
     */
    private String buildValidationDetails(String actualOutput, String expectedOutput) {
        return String.format("Expected contains: '%s', Actual: '%s'",
            truncate(expectedOutput, 100),
            truncate(actualOutput, 100));
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
