package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.plan.Plan;
import com.sxw.sxwaiagent.plan.PlanReviewService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/plans")
@Validated
public class PlanController {
    
    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    
    private final PlanReviewService planReviewService;
    
    public PlanController(PlanReviewService planReviewService) {
        this.planReviewService = planReviewService;
    }
    
    @GetMapping("/{planId}")
    public Result<Plan> getPlan(@PathVariable String planId) {
        Optional<Plan> plan = planReviewService.getPlan(planId);
        return plan.map(Result::ok).orElse(Result.error("Plan not found"));
    }
    
    @GetMapping("/chat/{chatId}")
    public Result<List<Plan>> getPlansByChat(@PathVariable String chatId) {
        List<Plan> plans = planReviewService.getPlansByChat(chatId);
        return Result.ok(plans);
    }
    
    @PostMapping("/{planId}/approve")
    public Result<String> approvePlan(
        @PathVariable String planId,
        @RequestParam @NotBlank String reviewedBy
    ) {
        log.info("Approving plan {} by {}", planId, reviewedBy);
        
        boolean success = planReviewService.approvePlan(planId, reviewedBy);
        
        if (success) {
            return Result.ok("Plan approved successfully");
        } else {
            return Result.error("Failed to approve plan (not found or not in PENDING status)");
        }
    }
    
    @PostMapping("/{planId}/reject")
    public Result<String> rejectPlan(
        @PathVariable String planId,
        @RequestParam @NotBlank String reviewedBy
    ) {
        log.info("Rejecting plan {} by {}", planId, reviewedBy);
        
        boolean success = planReviewService.rejectPlan(planId, reviewedBy);
        
        if (success) {
            return Result.ok("Plan rejected successfully");
        } else {
            return Result.error("Failed to reject plan (not found or not in PENDING status)");
        }
    }
}
