package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static com.sxw.sxwaiagent.hermes.HermesCandidate.CandidateStatus.*;

@Service
public class HermesCandidateService {
    private static final Logger log = LoggerFactory.getLogger(HermesCandidateService.class);
    private final HermesCandidateRepository repository;
    private final HermesApplicationService application;

    public HermesCandidateService(HermesCandidateRepository repository, HermesApplicationService application) {
        this.repository = repository;
        this.application = application;
    }

    public HermesCandidate createCandidate(String runId, String chatId, CandidateType type,
                                           String title, String content, String metadata) {
        var candidate = new HermesCandidate(UUID.randomUUID().toString(), runId, chatId, type, title,
                content, metadata, PENDING, null, Instant.now(), null, runId, null);
        repository.save(candidate);
        return candidate;
    }

    public Optional<HermesCandidate> getCandidate(String id) { return repository.findById(id); }
    public List<HermesCandidate> getPendingCandidates() { return repository.findByStatus(PENDING); }
    public List<HermesCandidate> getFailedCandidates() { return repository.findRecoverable(); }

    public boolean approveCandidate(String id, String reviewer) {
        // Durable approval precedes application, allowing recovery after a crash.
        if (!repository.transition(id, PENDING, APPROVED, reviewer)) return false;
        apply(id, reviewer);
        return true;
    }

    public boolean rejectCandidate(String id, String reviewer) {
        return repository.transition(id, PENDING, REJECTED, reviewer);
    }

    public boolean retryApply(String id, String reviewer) {
        var candidate = repository.findById(id).orElse(null);
        if (candidate == null) return false;
        if (candidate.status() == APPLY_FAILED) {
            if (!repository.transition(id, APPLY_FAILED, APPROVED, reviewer)) return false;
        } else if (candidate.status() != APPROVED || candidate.reviewedAt() == null
                || !candidate.reviewedAt().isBefore(Instant.now().minusSeconds(600))) {
            return false;
        }
        return apply(id, reviewer);
    }

    private boolean apply(String id, String reviewer) {
        try {
            return application.applyApproved(id);
        } catch (PessimisticLockingFailureException busy) {
            // Another application holds the row lock. Never change its state.
            return false;
        } catch (Exception e) {
            repository.transition(id, APPROVED, APPLY_FAILED, reviewer);
            log.error("Failed to apply Hermes candidate {}", id, e);
            return false;
        }
    }
}
