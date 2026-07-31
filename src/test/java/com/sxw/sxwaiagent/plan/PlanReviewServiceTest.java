package com.sxw.sxwaiagent.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlanReviewServiceTest {

    private PlanRepository planRepository;
    private PlanReviewService planReviewService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planReviewService = new PlanReviewService(planRepository);
    }

    @Test
    void createPlan_shouldSaveAndReturnPlan() {
        List<Plan.PlanStep> steps = List.of(
                new Plan.PlanStep(1, "Step 1", "searchRagFlow", Plan.StepStatus.PENDING),
                new Plan.PlanStep(2, "Step 2", "readFile", Plan.StepStatus.PENDING)
        );

        when(planRepository.savePlan(any(Plan.class))).thenReturn("plan-123");
        Plan savedPlan = new Plan("plan-123", "chat-1", "Test goal", steps,
                Plan.PlanStatus.PENDING, "agent", null, Instant.now(), null);
        when(planRepository.findByPlanId("plan-123")).thenReturn(Optional.of(savedPlan));

        Plan result = planReviewService.createPlan("chat-1", "Test goal", steps, "agent");

        assertEquals("plan-123", result.planId());
        assertEquals("chat-1", result.chatId());
        assertEquals("Test goal", result.goal());
        assertEquals(2, result.steps().size());
        assertEquals(Plan.PlanStatus.PENDING, result.status());
        verify(planRepository).savePlan(any(Plan.class));
    }

    @Test
    void approvePlan_shouldReturnTrueWhenPlanIsPending() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.PENDING, "agent", null, Instant.now(), null);
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        boolean result = planReviewService.approvePlan("plan-1", "reviewer");

        assertTrue(result);
        verify(planRepository).updateStatus("plan-1", Plan.PlanStatus.APPROVED, "reviewer");
    }

    @Test
    void approvePlan_shouldReturnFalseWhenPlanNotFound() {
        when(planRepository.findByPlanId("nonexistent")).thenReturn(Optional.empty());

        boolean result = planReviewService.approvePlan("nonexistent", "reviewer");

        assertFalse(result);
        verify(planRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    void approvePlan_shouldReturnFalseWhenPlanNotPending() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.APPROVED, "agent", null, Instant.now(), Instant.now());
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        boolean result = planReviewService.approvePlan("plan-1", "reviewer");

        assertFalse(result);
        verify(planRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    void rejectPlan_shouldReturnTrueWhenPlanIsPending() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.PENDING, "agent", null, Instant.now(), null);
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        boolean result = planReviewService.rejectPlan("plan-1", "reviewer");

        assertTrue(result);
        verify(planRepository).updateStatus("plan-1", Plan.PlanStatus.REJECTED, "reviewer");
    }

    @Test
    void rejectPlan_shouldReturnFalseWhenPlanNotFound() {
        when(planRepository.findByPlanId("nonexistent")).thenReturn(Optional.empty());

        boolean result = planReviewService.rejectPlan("nonexistent", "reviewer");

        assertFalse(result);
    }

    @Test
    void rejectPlan_shouldReturnFalseWhenPlanNotPending() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.DRAFT, "agent", null, Instant.now(), null);
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        boolean result = planReviewService.rejectPlan("plan-1", "reviewer");

        assertFalse(result);
        verify(planRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    void isPlanApproved_shouldReturnTrueWhenApproved() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.APPROVED, "agent", null, Instant.now(), Instant.now());
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        assertTrue(planReviewService.isPlanApproved("plan-1"));
    }

    @Test
    void isPlanApproved_shouldReturnFalseWhenPending() {
        Plan plan = new Plan("plan-1", "chat-1", "Goal", List.of(),
                Plan.PlanStatus.PENDING, "agent", null, Instant.now(), null);
        when(planRepository.findByPlanId("plan-1")).thenReturn(Optional.of(plan));

        assertFalse(planReviewService.isPlanApproved("plan-1"));
    }

    @Test
    void isPlanApproved_shouldReturnFalseWhenNotFound() {
        when(planRepository.findByPlanId("nonexistent")).thenReturn(Optional.empty());

        assertFalse(planReviewService.isPlanApproved("nonexistent"));
    }
}
