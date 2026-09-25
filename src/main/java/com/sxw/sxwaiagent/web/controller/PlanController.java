package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.plan.Plan;
import com.sxw.sxwaiagent.plan.PlanReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "计划管理", description = "Agent 执行计划的查询与审批")
@RestController
@RequestMapping("/api/plans")
@Validated
public class PlanController {
    
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    
    private final PlanReviewService planReviewService;
    
    public PlanController(PlanReviewService planReviewService) {
        this.planReviewService = planReviewService;
    }
    
    @Operation(summary = "获取计划详情")
    @GetMapping("/{planId}")
    public Result<Plan> getPlan(Authentication authentication, @PathVariable String planId) {
        Optional<Plan> plan = planReviewService.getPlan(currentUser(authentication), planId);
        return plan.map(Result::ok).orElse(Result.error("Plan not found"));
    }
    
    @Operation(summary = "获取会话关联的计划列表")
    @GetMapping("/chat/{chatId}")
    public Result<List<Plan>> getPlansByChat(Authentication authentication, @PathVariable String chatId) {
        List<Plan> plans = planReviewService.getPlansByChat(currentUser(authentication), chatId);
        return Result.ok(plans);
    }
    
    @Operation(summary = "审批通过计划", description = "将计划状态变更为 APPROVED，允许执行")
    @PostMapping("/{planId}/approve")
    public Result<String> approvePlan(
        Authentication authentication,
        @PathVariable String planId
    ) {
        String reviewedBy = ((AuthenticatedUser) authentication.getPrincipal()).username();
        log.info("Approving plan {} by {}", planId, reviewedBy);
        
        boolean success = planReviewService.approvePlan(currentUser(authentication), planId, reviewedBy);
        
        if (success) {
            return Result.ok("Plan approved successfully");
        } else {
            return Result.error("Failed to approve plan (not found or not in PENDING status)");
        }
    }
    
    @Operation(summary = "拒绝计划")
    @PostMapping("/{planId}/reject")
    public Result<String> rejectPlan(
        Authentication authentication,
        @PathVariable String planId
    ) {
        String reviewedBy = ((AuthenticatedUser) authentication.getPrincipal()).username();
        log.info("Rejecting plan {} by {}", planId, reviewedBy);
        
        boolean success = planReviewService.rejectPlan(currentUser(authentication), planId, reviewedBy);
        
        if (success) {
            return Result.ok("Plan rejected successfully");
        } else {
            return Result.error("Failed to reject plan (not found or not in PENDING status)");
        }
    }

    private static long currentUser(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
