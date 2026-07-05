package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Hermes candidate service.
 * <p>
 * Provides query, review, apply and management features for candidates.
 */
@Service
public class HermesCandidateService {
    
    private static final Logger log = LoggerFactory.getLogger(HermesCandidateService.class);
    
    private final HermesCandidateRepository candidateRepository;
    private final HermesApplier hermesApplier;
    
    public HermesCandidateService(
        HermesCandidateRepository candidateRepository,
        HermesApplier hermesApplier
    ) {
        this.candidateRepository = candidateRepository;
        this.hermesApplier = hermesApplier;
    }
    
    /**
     * Find all pending candidates.
     */
    public List<HermesCandidate> findAllPending() {
        return candidateRepository.findAllPending();
    }
    
    /**
     * Find candidates by status.
     */
    public List<HermesCandidate> findByStatus(HermesCandidateStatus status) {
        return candidateRepository.findByStatus(status);
    }
    
    /**
     * Find candidates by type and status.
     */
    public List<HermesCandidate> findByTypeAndStatus(HermesCandidateType type, HermesCandidateStatus status) {
        return candidateRepository.findByTypeAndStatus(type, status);
    }
    
    /**
     * Find all candidates for a given trace.
     */
    public List<HermesCandidate> findByTraceId(String traceId) {
        return candidateRepository.findByTraceId(traceId);
    }
    
    /**
     * Find by candidateId.
     */
    public Optional<HermesCandidate> findByCandidateId(String candidateId) {
        return candidateRepository.findByCandidateId(candidateId);
    }
    
    /**
     * Approve candidate.
     */
    public void approve(String candidateId, String reviewedBy) {
        Optional<HermesCandidate> opt = candidateRepository.findByCandidateId(candidateId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Candidate not found: " + candidateId);
        }
        
        HermesCandidate candidate = opt.get();
        if (!candidate.canReview()) {
            throw new IllegalStateException("Candidate cannot be reviewed: " + candidate.status());
        }
        
        candidateRepository.updateStatus(candidateId, HermesCandidateStatus.APPROVED, reviewedBy);
        log.info("Candidate approved: {} by {}", candidateId, reviewedBy);
    }
    
    /**
     * 拒绝候选
     */
    public void reject(String candidateId, String reviewedBy) {
        Optional<HermesCandidate> opt = candidateRepository.findByCandidateId(candidateId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Candidate not found: " + candidateId);
        }
        
        HermesCandidate candidate = opt.get();
        if (!candidate.canReview()) {
            throw new IllegalStateException("Candidate cannot be reviewed: " + candidate.status());
        }
        
        candidateRepository.updateStatus(candidateId, HermesCandidateStatus.REJECTED, reviewedBy);
        log.info("Candidate rejected: {} by {}", candidateId, reviewedBy);
    }
    
    /**
     * 应用已批准的候选
     */
    public void apply(String candidateId) {
        Optional<HermesCandidate> opt = candidateRepository.findByCandidateId(candidateId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Candidate not found: " + candidateId);
        }
        
        HermesCandidate candidate = opt.get();
        if (!candidate.canApply()) {
            throw new IllegalStateException("Candidate cannot be applied: " + candidate.status());
        }
        
        try {
            String result = hermesApplier.apply(candidate);
            candidateRepository.updateApplyResult(candidateId, HermesCandidateStatus.APPLIED, result);
            log.info("Candidate applied: {} - {}", candidateId, result);
        } catch (Exception e) {
            candidateRepository.updateApplyResult(candidateId, HermesCandidateStatus.FAILED, e.getMessage());
            log.error("Candidate apply failed: {} - {}", candidateId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 统计待审核数量
     */
    public long countPending() {
        return candidateRepository.countPending();
    }
}
