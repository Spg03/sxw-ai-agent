package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.plan.Plan;
import com.sxw.sxwaiagent.plan.PlanReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExitPlanModeTool {
    
    private static final Logger log = LoggerFactory.getLogger(ExitPlanModeTool.class);
    
    private final PlanReviewService planReviewService;
    
    public ExitPlanModeTool(PlanReviewService planReviewService) {
        this.planReviewService = planReviewService;
    }
    
    @Tool(description = """
        退出规划模式并提交计划供用户审核。
        提交后，计划状态变为 PENDING，等待用户批准或拒绝。
        用户批准后，Agent 可以进入 EXECUTE 模式按计划执行。
        """)
    public String exitPlanMode(
        @ToolParam(description = "计划 ID") String planId
    ) {
        log.info("Exiting plan mode for plan {}", planId);
        
        Optional<Plan> planOpt = planReviewService.getPlan(planId);
        
        if (planOpt.isEmpty()) {
            return "计划不存在: " + planId;
        }
        
        Plan plan = planOpt.get();
        
        return String.format("""
            已退出规划模式。
            
            计划 ID: %s
            状态: %s
            目标: %s
            步骤数: %d
            
            计划已提交审核，等待用户批准。
            用户批准后，可以使用该计划 ID 进入 EXECUTE 模式执行任务。
            
            审核接口:
            - 批准: POST /api/plans/%s/approve
            - 拒绝: POST /api/plans/%s/reject
            """,
            plan.planId(),
            plan.status(),
            plan.goal(),
            plan.steps().size(),
            plan.planId(),
            plan.planId()
        );
    }
}
