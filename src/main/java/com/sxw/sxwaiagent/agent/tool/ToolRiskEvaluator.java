package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.ToolPolicy;
import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 工具风险评估器
 * <p>
 * 根据 Profile 的 ToolPolicy 评估工具调用是否允许执行。
 * 检查工具是否在 Profile 允许列表中，风险等级是否在允许范围内。
 */
@Component
public class ToolRiskEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ToolRiskEvaluator.class);

    private final ToolRegistry toolRegistry;

    public ToolRiskEvaluator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 评估工具调用是否允许
     *
     * @param toolName 工具名称
     * @param profile  Agent Profile
     * @return 评估结果
     */
    public EvaluationResult evaluate(String toolName, AgentProfile profile) {
        // 1. 检查工具是否存在
        Optional<ToolDefinition> defOpt = toolRegistry.get(toolName);
        if (defOpt.isEmpty()) {
            log.warn("Tool not found in registry: {}", toolName);
            return EvaluationResult.rejected("Tool not registered: " + toolName, "NOT_REGISTERED");
        }

        ToolDefinition def = defOpt.get();
        ToolPolicy policy = profile.toolPolicy();

        // 2. 检查 Profile 是否启用该工具
        if (!def.isEnabledFor(profile.code())) {
            log.warn("Tool {} is not enabled for profile {}", toolName, profile.code());
            return EvaluationResult.rejected(
                    "Tool " + toolName + " is not enabled for profile " + profile.code(),
                    "PROFILE_DISABLED"
            );
        }

        // 3. 检查风险等级是否在允许范围内
        if (!def.isWithinRiskLimit(policy.maxRiskLevel())) {
            log.warn("Tool {} risk level {} exceeds policy max {}",
                    toolName, def.riskLevel(), policy.maxRiskLevel());
            return EvaluationResult.rejected(
                    "Tool " + toolName + " risk level " + def.riskLevel() +
                            " exceeds policy max " + policy.maxRiskLevel(),
                    "RISK_EXCEEDED"
            );
        }

        // 4. 检查是否需要审批
        boolean needsApproval = def.requiresApproval() || policy.requiresApproval();

        // 5. 检查是否需要审计
        boolean needsAudit = policy.auditEnabled();

        log.debug("Tool {} evaluation: allowed=true, needsApproval={}, needsAudit={}",
                toolName, needsApproval, needsAudit);

        return EvaluationResult.allowed(needsApproval, needsAudit);
    }

    /**
     * 评估结果
     */
    public record EvaluationResult(
            boolean allowed,
            boolean needsApproval,
            boolean needsAudit,
            String rejectReason,
            String rejectCode
    ) {

        public static EvaluationResult allowed(boolean needsApproval, boolean needsAudit) {
            return new EvaluationResult(true, needsApproval, needsAudit, null, null);
        }

        public static EvaluationResult rejected(String reason, String code) {
            return new EvaluationResult(false, false, false, reason, code);
        }

        public boolean isRejected() {
            return !allowed;
        }
    }
}
