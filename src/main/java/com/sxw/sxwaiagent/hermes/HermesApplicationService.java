package com.sxw.sxwaiagent.hermes;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes application and commits DB effects together with APPLIED. */
@Service
public class HermesApplicationService {
    private final HermesCandidateRepository repository;
    private final HermesApplier applier;

    public HermesApplicationService(HermesCandidateRepository repository, HermesApplier applier) {
        this.repository = repository;
        this.applier = applier;
    }

    @Transactional
    public boolean applyApproved(String id) {
        var candidate = repository.findByIdForUpdate(id).orElse(null);
        if (candidate == null || !candidate.canApply()) return false;
        applier.apply(candidate);
        if (!repository.transition(id, HermesCandidate.CandidateStatus.APPROVED,
                HermesCandidate.CandidateStatus.APPLIED, candidate.reviewedBy())) {
            throw new IllegalStateException("Candidate changed during application");
        }
        return true;
    }
}
