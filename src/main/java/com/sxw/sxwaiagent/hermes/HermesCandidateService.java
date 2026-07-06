package com.sxw.sxwaiagent.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HermesCandidateService {
    
    private static final Logger log = LoggerFactory.getLogger(HermesCandidateService.class);
    
    private final HermesCandidateRepository repository;
    
    public HermesCandidateService(HermesCandidateRepository repository) {
        this.repository = repository;
    }
    
    public HermesCandidate createCandidate(
        String runId,
        String chatId,
        CandidateType type,
        String title,
        String content,
        String metadata
    ) {
        HermesCandidate candidate = new HermesCandidate(
            UUID.randomUUID().toString(),
            runId,
            chatId,
            type,
            title,
            content,
            metadata,
            HermesCandidate.CandidateStatus.PENDING,
            null,
            Instant.now(),
            null
        );
        
        repository.save(candidate);
        log.info("Created Hermes candidate: {} ({}) for run {}", title, type, runId);
        return candidate;
    }
    
    public Optional<HermesCandidate> getCandidate(String candidateId) {
        return repository.findById(candidateId);
    }
    
    public List<HermesCandidate> getPendingCandidates() {
        return repository.findByStatus(HermesCandidate.CandidateStatus.PENDING);
    }
    
    public boolean approveCandidate(String candidateId, String reviewedBy) {
        Optional<HermesCandidate> candidateOpt = repository.findById(candidateId);
        
        if (candidateOpt.isEmpty()) {
            log.warn("Candidate not found: {}", candidateId);
            return false;
        }
        
        HermesCandidate candidate = candidateOpt.get();
        if (candidate.status() != HermesCandidate.CandidateStatus.PENDING) {
            log.warn("Candidate {} is not in PENDING status (current: {})", candidateId, candidate.status());
            return false;
        }
        
        repository.updateStatus(candidateId, HermesCandidate.CandidateStatus.APPROVED, reviewedBy);
        log.info("Approved candidate {} by {}", candidateId, reviewedBy);
        
        // TODO: Apply approved candidate to appropriate system
        // - MEMORY -> MemoryService
        // - KNOWLEDGE -> KnowledgeService
        // - EVAL_CASE -> EvalCaseService
        // - PROMPT_HINT -> PromptService
        // - TOOL_PATTERN -> ToolRegistry
        
        return true;
    }
    
    public boolean rejectCandidate(String candidateId, String reviewedBy) {
        Optional<HermesCandidate> candidateOpt = repository.findById(candidateId);
        
        if (candidateOpt.isEmpty()) {
            log.warn("Candidate not found: {}", candidateId);
            return false;
        }
        
        HermesCandidate candidate = candidateOpt.get();
        if (candidate.status() != HermesCandidate.CandidateStatus.PENDING) {
            log.warn("Candidate {} is not in PENDING status (current: {})", candidateId, candidate.status());
            return false;
        }
        
        repository.updateStatus(candidateId, HermesCandidate.CandidateStatus.REJECTED, reviewedBy);
        log.info("Rejected candidate {} by {}", candidateId, reviewedBy);
        return true;
    }
}
