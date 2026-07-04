package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import com.sxw.sxwaiagent.agent.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一工具执行器（P3 增强版）
 * <p>
 * 执行链路：
 * ToolCall → ToolRegistry → ToolRiskEvaluator → ApprovalService → Execute → ToolAuditLog
 * <p>
 * P3 新增：风险检查、审批服务、审计日志
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolCallback[] allTools;
    private final TraceRecorder traceRecorder;
    private final ToolRegistry toolRegistry;
    private final ToolRiskEvaluator riskEvaluator;
    private final ApprovalService approvalService;
    private final ToolAuditLog auditLog;
    private final Map<String, ToolCallback> toolMap;

    public ToolExecutor(
            ToolCallback[] allTools,
            TraceRecorder traceRecorder,
            ToolRegistry toolRegistry,
            ToolRiskEvaluator riskEvaluator,
            ApprovalService approvalService,
            ToolAuditLog auditLog
    ) {
        this.allTools = allTools;
        this.traceRecorder = traceRecorder;
        this.toolRegistry = toolRegistry;
        this.riskEvaluator = riskEvaluator;
        this.approvalService = approvalService;
        this.auditLog = auditLog;
        this.toolMap = new HashMap<>();

        for (ToolCallback tool : allTools) {
            String toolName = tool.getToolDefinition().name();
            toolMap.put(toolName, tool);
        }

        log.info("ToolExecutor initialized with {} tools (governance enabled)", toolMap.size());
    }

    /**
     * 执行工具（带完整治理链路）
     *
     * @param toolName   工具名称
     * @param arguments  工具参数（JSON 字符串）
     * @param requestId  请求 ID
     * @param traceId    追踪 ID
     * @param turn       轮次
     * @param profile    Agent Profile（用于风险评估）
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, String arguments, String requestId, String traceId, int turn, AgentProfile profile) {
        long startTime = System.currentTimeMillis();

        log.info("[{}] Executing tool: {} (turn {})", requestId, toolName, turn);

        // 1. 检查 Spring AI 层面工具是否存在
        ToolCallback tool = toolMap.get(toolName);
        if (tool == null) {
            String errorMsg = "Tool not found: " + toolName;
            log.warn("[{}] {}", requestId, errorMsg);
            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, errorMsg, "failed", 0);
            auditLog.record(requestId, traceId, turn, toolName, null, arguments, errorMsg, "not_found", 0, false, null);
            return ToolResult.failure(errorMsg);
        }

        // 2. 风险评估
        ToolRiskLevel riskLevel = null;
        if (profile != null) {
            ToolRiskEvaluator.EvaluationResult eval = riskEvaluator.evaluate(toolName, profile);
            riskLevel = toolRegistry.get(toolName).map(ToolDefinition::riskLevel).orElse(null);

            if (eval.isRejected()) {
                log.warn("[{}] Tool {} rejected: {}", requestId, toolName, eval.rejectReason());
                auditLog.record(requestId, traceId, turn, toolName, riskLevel, arguments, eval.rejectReason(), "rejected", 0, false, null);
                traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, eval.rejectReason(), "rejected", 0);
                return ToolResult.failure("Tool rejected: " + eval.rejectReason());
            }

            // 3. 审批检查（如果需要审批，创建审批请求并拒绝执行）
            if (eval.needsApproval()) {
                ApprovalService.ApprovalRequest approvalReq = approvalService.createApprovalRequest(
                        requestId, traceId, toolName, arguments,
                        "Tool " + toolName + " requires approval (riskLevel=" + riskLevel + ")"
                );
                log.info("[{}] Tool {} requires approval: {}", requestId, toolName, approvalReq.approvalId());
                auditLog.record(requestId, traceId, turn, toolName, riskLevel, arguments, "pending_approval", "pending_approval", 0, false, approvalReq.approvalId());
                return ToolResult.failure("Tool requires approval: " + approvalReq.approvalId() + ". Please approve before retrying.");
            }
        }

        // 4. 执行工具
        try {
            String result = tool.call(arguments);
            long latencyMs = System.currentTimeMillis() - startTime;

            log.info("[{}] Tool {} completed in {}ms", requestId, toolName, latencyMs);

            // 记录追踪
            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, result, "success", latencyMs);

            // 记录审计
            auditLog.record(requestId, traceId, turn, toolName, riskLevel, arguments, result, "success", latencyMs, true, null);

            return ToolResult.success(result);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            String errorMsg = "Tool execution failed: " + e.getMessage();
            log.error("[{}] Tool {} failed: {}", requestId, toolName, errorMsg, e);

            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, errorMsg, "failed", latencyMs);
            auditLog.record(requestId, traceId, turn, toolName, riskLevel, arguments, errorMsg, "failed", latencyMs, true, null);

            return ToolResult.failure(errorMsg);
        }
    }

    /**
     * 执行工具（向后兼容，无 Profile 上下文）
     * <p>
     * 不执行风险评估，仅记录审计。用于 LegacyReActRuntime 等旧路径。
     */
    public ToolResult execute(String toolName, String arguments, String requestId, String traceId, int turn) {
        return execute(toolName, arguments, requestId, traceId, turn, null);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        return toolMap.containsKey(toolName);
    }
}
