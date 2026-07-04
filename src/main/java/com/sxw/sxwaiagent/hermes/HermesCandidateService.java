package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Hermes 候选服务
 * <p>
 * 提供候选的查询、审核、应用等管理功能。
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
     * 查看所有待审核候选
     */
    public List<HermesCandidate> findAllPending() {
        return candidateRepository.findAllPending();
    }
    
    /**
     * 根据状态查看候选
     */
    public List<HermesCandidate> findByStatus(HermesCandidateStatus status) {
        return candidateRepository.findByStatus(status);
    }
    
    /**
     * 根据类型和状态查看候选
     */
    public List<HermesCandidate> findByTypeAndStatus(HermesCandidateType type, HermesCandidateStatus status) {
        return candidateRepository.findByTypeAndStatus(type, status);
    }
    
    /**
     * 查看指定 Trace 的所有候选
     */
    public List<HermesCandidate> findByTraceId(String traceId) {
        return candidateRepository.findByTraceId(traceId);
    }
    
    /**
     * 根据 candidateId 查找
     */
    public Optional<HermesCandidate> findByCandidateId(String candidateId) {
        return candidateRepository.findByCandidateId(candidateId);
    }
    
    /**
     * 批准候选
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
