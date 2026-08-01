package com.sxw.sxwaiagent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.tool.ToolExecutor;
import com.sxw.sxwaiagent.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 计划执行器
 * <p>
 * 按 Plan steps 顺序执行，更新每个 step 的状态：
 * PENDING → IN_PROGRESS → COMPLETED / FAILED
 * 所有步骤完成后更新 Plan status 为 COMPLETED。
 */
@Component
public class PlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanExecutor.class);

    private final PlanRepository planRepository;
    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public PlanExecutor(PlanRepository planRepository, ToolExecutor toolExecutor) {
        this.planRepository = planRepository;
        this.toolExecutor = toolExecutor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行指定计划的所有步骤
     *
     * @param planId    计划 ID
     * @param requestId 请求 ID（用于追踪）
     * @param traceId   追踪 ID
     * @return 执行结果摘要
     */
    public ExecutionResult executePlan(String planId, String requestId, String traceId) {
        Optional<Plan> planOpt = planRepository.findByPlanId(planId);
        if (planOpt.isEmpty()) {
            log.warn("Plan not found: {}", planId);
            return new ExecutionResult(planId, false, "Plan not found: " + planId, List.of());
        }

        Plan plan = planOpt.get();

        if (plan.status() != Plan.PlanStatus.APPROVED) {
            log.warn("Plan {} is not APPROVED (current: {})", planId, plan.status());
            return new ExecutionResult(planId, false,
                    "Plan is not approved (status: " + plan.status() + "), cannot execute.", List.of());
        }

        log.info("Starting execution of plan {} with {} steps", planId, plan.steps().size());

        List<StepResult> stepResults = new ArrayList<>();
        boolean allSuccess = true;

        for (Plan.PlanStep step : plan.steps()) {
            log.info("[Plan {}] Executing step {}: {}", planId, step.stepIndex(), step.description());

            // 标记为 IN_PROGRESS
            planRepository.updateStepStatus(planId, step.stepIndex(), Plan.StepStatus.IN_PROGRESS);

            try {
                // 执行步骤（如果有 toolName，调用对应工具；否则仅记录描述）
                String result;
                if (step.toolName() != null && !step.toolName().isEmpty()) {
                    // 将步骤描述作为工具参数（JSON 格式）
                    String args = buildStepArguments(step);
                    ToolResult toolResult = toolExecutor.execute(
                            step.toolName(), args, requestId, traceId, step.stepIndex()
                    );
                    result = toolResult.content();
                    if (toolResult.success()) {
                        planRepository.updateStepStatus(planId, step.stepIndex(), Plan.StepStatus.COMPLETED);
                        stepResults.add(new StepResult(step.stepIndex(), true, result));
                    } else {
                        planRepository.updateStepStatus(planId, step.stepIndex(), Plan.StepStatus.FAILED);
                        stepResults.add(new StepResult(step.stepIndex(), false, result));
                        allSuccess = false;
                        log.warn("[Plan {}] Step {} failed: {}", planId, step.stepIndex(), result);
                        // 继续执行后续步骤，不中断
                    }
                } else {
                    // 无工具名的步骤，视为信息步骤，直接标记完成
                    result = step.description();
                    planRepository.updateStepStatus(planId, step.stepIndex(), Plan.StepStatus.COMPLETED);
                    stepResults.add(new StepResult(step.stepIndex(), true, result));
                }

                log.info("[Plan {}] Step {} completed: {}", planId, step.stepIndex(),
                        stepResults.get(stepResults.size() - 1).success());

            } catch (Exception e) {
                log.error("[Plan {}] Step {} threw exception", planId, step.stepIndex(), e);
                planRepository.updateStepStatus(planId, step.stepIndex(), Plan.StepStatus.FAILED);
                stepResults.add(new StepResult(step.stepIndex(), false, "Exception: " + e.getMessage()));
                allSuccess = false;
            }
        }

        // 所有步骤完成后更新 Plan status
        Plan.PlanStatus finalStatus = allSuccess ? Plan.PlanStatus.COMPLETED : Plan.PlanStatus.APPROVED;
        // 即使部分失败，也不自动改为 FAILED，保留 APPROVED 以便重试
        if (allSuccess) {
            planRepository.updateStatus(planId, Plan.PlanStatus.COMPLETED, "system");
            log.info("Plan {} completed successfully", planId);
        } else {
            log.warn("Plan {} completed with failures", planId);
        }

        return new ExecutionResult(planId, allSuccess,
                allSuccess ? "All steps completed successfully." : "Some steps failed.",
                stepResults);
    }

    /**
     * 构建步骤的工具参数
     */
    private String buildStepArguments(Plan.PlanStep step) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "description", step.description() != null ? step.description() : "",
                    "stepIndex", step.stepIndex()
            ));
        } catch (Exception e) {
            return "{\"description\":\"" + step.description() + "\"}";
        }
    }

    /**
     * 计划执行结果
     */
    public record ExecutionResult(
            String planId,
            boolean success,
            String message,
            List<StepResult> stepResults
    ) {}

    /**
     * 步骤执行结果
     */
    public record StepResult(
            int stepIndex,
            boolean success,
            String result
    ) {}
}
