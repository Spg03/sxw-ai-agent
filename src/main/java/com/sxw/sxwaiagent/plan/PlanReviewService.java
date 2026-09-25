package com.sxw.sxwaiagent.plan;

import com.sxw.sxwaiagent.conversation.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PlanReviewService {
    
    private static final Logger log = LoggerFactory.getLogger(PlanReviewService.class);
    
    private final PlanRepository planRepository;
    private final ConversationService conversationService;
    
    @Autowired
    public PlanReviewService(PlanRepository planRepository, ConversationService conversationService) {
        this.planRepository = planRepository;
        this.conversationService = conversationService;
    }

    /** Compatibility constructor for legacy callers. Owner-aware methods require the main constructor. */
    @Deprecated
    public PlanReviewService(PlanRepository planRepository) {
        this.planRepository = planRepository;
        this.conversationService = null;
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

    public Optional<Plan> getPlan(long userId, String planId) {
        return planRepository.findByPlanId(planId).filter(plan -> owns(userId, plan));
    }
    
    public List<Plan> getPlansByChat(String chatId) {
        return planRepository.findByChatId(chatId);
    }

    public List<Plan> getPlansByChat(long userId, String chatId) {
        conversationService.get(userId, chatId);
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

    public boolean approvePlan(long userId, String planId, String reviewedBy) {
        Optional<Plan> plan = getPlan(userId, planId);
        return plan.isPresent() && approvePlan(planId, reviewedBy);
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

    public boolean rejectPlan(long userId, String planId, String reviewedBy) {
        Optional<Plan> plan = getPlan(userId, planId);
        return plan.isPresent() && rejectPlan(planId, reviewedBy);
    }
    
    public boolean isPlanApproved(String planId) {
        return planRepository.findByPlanId(planId)
            .map(p -> p.status() == Plan.PlanStatus.APPROVED)
            .orElse(false);
    }

    public boolean isPlanApproved(long userId, String chatId, String planId) {
        return getPlan(userId, planId)
            .filter(plan -> plan.chatId().equals(chatId))
            .map(plan -> plan.status() == Plan.PlanStatus.APPROVED)
            .orElse(false);
    }

    private boolean owns(long userId, Plan plan) {
        if (conversationService == null) return false;
        try {
            conversationService.get(userId, plan.chatId());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
