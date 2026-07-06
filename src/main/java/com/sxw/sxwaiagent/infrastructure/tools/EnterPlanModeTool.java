package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.plan.AgentRunMode;
import com.sxw.sxwaiagent.plan.Plan;
import com.sxw.sxwaiagent.plan.PlanReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnterPlanModeTool {
    
    private static final Logger log = LoggerFactory.getLogger(EnterPlanModeTool.class);
    
    private final PlanReviewService planReviewService;
    
    public EnterPlanModeTool(PlanReviewService planReviewService) {
        this.planReviewService = planReviewService;
    }
    
    @Tool(description = """
        进入规划模式，用于复杂任务的分解和规划。
        在规划模式下，只能使用只读工具（如 searchRagFlow, readFile, listNotes 等），
        不能执行写操作（如 writeFile, executeTerminalCommand 等）。
        规划完成后，使用 exitPlanMode 工具退出并提交计划供用户审核。
        """)
    public String enterPlanMode(
        @ToolParam(description = "聊天会话 ID") String chatId,
        @ToolParam(description = "任务目标描述") String goal,
        @ToolParam(description = "计划步骤列表，JSON 格式：[{\"stepIndex\": 1, \"description\": \"步骤描述\", \"toolName\": \"工具名\"}]") String stepsJson
    ) {
        log.info("Entering plan mode for chat {} with goal: {}", chatId, goal);
        
        try {
            // Parse steps from JSON
            List<Plan.PlanStep> steps = parseSteps(stepsJson);
            
            // Create plan
            Plan plan = planReviewService.createPlan(
                chatId,
                goal,
                steps,
                "agent"
            );
            
            return String.format("""
                已进入规划模式。
                
                计划 ID: %s
                目标: %s
                步骤数: %d
                
                当前模式限制:
                - ✅ 允许: searchRagFlow, readFile, listNotes, searchNotes, readNote
                - ❌ 禁止: writeFile, executeTerminalCommand, doTerminate 等写操作
                
                请完成规划后，使用 exitPlanMode 工具提交计划供用户审核。
                """,
                plan.planId(),
                goal,
                steps.size()
            );
        } catch (Exception e) {
            log.error("Failed to enter plan mode", e);
            return "进入规划模式失败: " + e.getMessage();
        }
    }
    
    private List<Plan.PlanStep> parseSteps(String stepsJson) {
        // Simple JSON parsing - in production, use Jackson or similar
        // For now, return a default step
        return List.of(
            new Plan.PlanStep(1, "执行任务", null, Plan.StepStatus.PENDING)
        );
    }
}
