package com.sxw.sxwaiagent.hermes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import static com.sxw.sxwaiagent.hermes.HermesCandidate.CandidateStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HermesCandidateServiceTest {
    private HermesCandidateRepository repository;
    private HermesApplier applier;
    private HermesCandidateService service;

    @BeforeEach void setUp() {
        repository = mock(HermesCandidateRepository.class);
        applier = mock(HermesApplier.class);
        service = new HermesCandidateService(repository, new HermesApplicationService(repository, applier));
    }

    private HermesCandidate candidate(HermesCandidate.CandidateStatus status) {
        return new HermesCandidate("c", "run", "chat", CandidateType.MEMORY, "title", "content", null,
                status, "admin", Instant.now(), Instant.now().minusSeconds(700), "run", null);
    }

    @Test void createIsPendingAndNotApplied() {
        var created = service.createCandidate("run", "chat", CandidateType.MEMORY, "title", "text", null);
        assertEquals(PENDING, created.status());
        verify(repository).save(created);
        verifyNoInteractions(applier);
    }

    @Test void approveAppliesOnlyAfterAtomicClaim() {
        when(repository.transition("c", PENDING, APPROVED, "admin")).thenReturn(true);
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(repository.transition("c", APPROVED, APPLIED, "admin")).thenReturn(true);
        assertTrue(service.approveCandidate("c", "admin"));
        verify(applier).apply(argThat(HermesCandidate::canApply));
        verify(repository).transition("c", APPROVED, APPLIED, "admin");
    }

    @Test void concurrentApprovalLoserNeverApplies() {
        when(repository.transition("c", PENDING, APPROVED, "admin")).thenReturn(false);
        assertFalse(service.approveCandidate("c", "admin"));
        verifyNoInteractions(applier);
    }

    @Test void applicationFailureIsRetryable() {
        when(repository.transition("c", PENDING, APPROVED, "admin")).thenReturn(true);
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(applier.apply(any())).thenThrow(new IllegalStateException("failed"));
        assertTrue(service.approveCandidate("c", "admin"));
        verify(repository).transition("c", APPROVED, APPLY_FAILED, "admin");
    }

    @Test void retryPassesApprovedCandidateToRealApplicationLayer() {
        when(repository.findById("c")).thenReturn(Optional.of(candidate(APPLY_FAILED)));
        when(repository.transition("c", APPLY_FAILED, APPROVED, "admin")).thenReturn(true);
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(repository.transition("c", APPROVED, APPLIED, "admin")).thenReturn(true);
        when(applier.apply(any())).thenAnswer(call -> {
            HermesCandidate c = call.getArgument(0);
            assertTrue(c.canApply(), "Retry must not pass APPLY_FAILED to the applier");
            return "applied";
        });
        assertTrue(service.retryApply("c", "admin"));
    }

    @Test void interruptedApprovalCanBeRecovered() {
        when(repository.findById("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(repository.transition("c", APPROVED, APPLIED, "admin")).thenReturn(true);
        assertTrue(service.retryApply("c", "admin"));
    }

    @Test void retryDoesNotOverwriteRunningApplication() {
        when(repository.findById("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(repository.findByIdForUpdate("c")).thenThrow(new org.springframework.dao.CannotAcquireLockException("busy"));
        assertFalse(service.retryApply("c", "admin"));
        verify(repository, never()).transition(anyString(), any(), eq(APPLY_FAILED), anyString());
        verifyNoInteractions(applier);
    }

    @Test void rejectsOnlyPendingCandidates() {
        when(repository.transition("c", PENDING, REJECTED, "admin")).thenReturn(true);
        assertTrue(service.rejectCandidate("c", "admin"));
        verifyNoInteractions(applier);
    }

    @Test void appliedOrMissingCandidatesCannotBeRetried() {
        when(repository.findById("c")).thenReturn(Optional.of(candidate(APPLIED)));
        assertFalse(service.retryApply("c", "admin"));
        assertFalse(service.retryApply("missing", "admin"));
        verifyNoInteractions(applier);
    }

    @Test void retryFailureLeavesRecoverableState() {
        when(repository.findById("c")).thenReturn(Optional.of(candidate(APPLY_FAILED)));
        when(repository.transition("c", APPLY_FAILED, APPROVED, "admin")).thenReturn(true);
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPROVED)));
        when(applier.apply(any())).thenThrow(new IllegalStateException("again"));
        assertFalse(service.retryApply("c", "admin"));
        verify(repository).transition("c", APPROVED, APPLY_FAILED, "admin");
    }

    @Test void failedListIncludesInterruptedApprovals() {
        var items = List.of(candidate(APPLY_FAILED), candidate(APPROVED));
        when(repository.findRecoverable()).thenReturn(items);
        assertEquals(items, service.getFailedCandidates());
    }

    @Test void applicationDoesNotRepeatCompletedWrites() {
        when(repository.findByIdForUpdate("c")).thenReturn(Optional.of(candidate(APPLIED)));
        assertFalse(new HermesApplicationService(repository, applier).applyApproved("c"));
        verifyNoInteractions(applier);
    }
}
