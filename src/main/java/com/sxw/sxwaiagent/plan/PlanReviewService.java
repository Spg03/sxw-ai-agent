package com.sxw.sxwaiagent.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PlanReviewService {
    
    private static final Logger log = LoggerFactory.getLogger(PlanReviewService.class);
    
    private final PlanRepository planRepository;
    
    public PlanReviewService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }
    
    public Plan createPlan(String chatId, String goal, List<Plan.PlanStep> steps, String createdBy) {
        Plan plan = new Plan(
            null,
            chatId,
            goal,
            steps,
            Plan.PlanStatus.PENDING,
            createdBy,
            null,
            Instant.now(),
            null
        );
        
        String planId = planRepository.savePlan(plan);
        log.info("Created plan {} for chat {}", planId, chatId);
        
        return planRepository.findByPlanId(planId).orElse(plan);
    }
    
    public Optional<Plan> getPlan(String planId) {
        return planRepository.findByPlanId(planId);
    }
    
    public List<Plan> getPlansByChat(String chatId) {
        return planRepository.findByChatId(chatId);
    }
    
    public boolean approvePlan(String planId, String reviewedBy) {
        Optional<Plan> planOpt = planRepository.findByPlanId(planId);
        
        if (planOpt.isEmpty()) {
            log.warn("Plan not found: {}", planId);
            return false;
        }
        
        Plan plan = planOpt.get();
        if (plan.status() != Plan.PlanStatus.PENDING) {
            log.warn("Plan {} is not in PENDING status (current: {})", planId, plan.status());
            return false;
        }
        
        planRepository.updateStatus(planId, Plan.PlanStatus.APPROVED, reviewedBy);
        log.info("Plan {} approved by {}", planId, reviewedBy);
        return true;
    }
    
    public boolean rejectPlan(String planId, String reviewedBy) {
        Optional<Plan> planOpt = planRepository.findByPlanId(planId);
        
        if (planOpt.isEmpty()) {
            log.warn("Plan not found: {}", planId);
            return false;
        }
        
        Plan plan = planOpt.get();
        if (plan.status() != Plan.PlanStatus.PENDING) {
            log.warn("Plan {} is not in PENDING status (current: {})", planId, plan.status());
            return false;
        }
        
        planRepository.updateStatus(planId, Plan.PlanStatus.REJECTED, reviewedBy);
        log.info("Plan {} rejected by {}", planId, reviewedBy);
        return true;
    }
    
    public boolean isPlanApproved(String planId) {
        return planRepository.findByPlanId(planId)
            .map(p -> p.status() == Plan.PlanStatus.APPROVED)
            .orElse(false);
    }
}
