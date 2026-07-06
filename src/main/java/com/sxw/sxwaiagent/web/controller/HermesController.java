package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.hermes.HermesCandidate;
import com.sxw.sxwaiagent.hermes.HermesCandidateService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/hermes/candidates")
@Validated
public class HermesController {
    
    private static final Logger log = LoggerFactory.getLogger(HermesController.class);
    
    private final HermesCandidateService candidateService;
    
    public HermesController(HermesCandidateService candidateService) {
        this.candidateService = candidateService;
    }
    
    @GetMapping
    public Result<List<HermesCandidate>> getPendingCandidates() {
        List<HermesCandidate> candidates = candidateService.getPendingCandidates();
        return Result.ok(candidates);
    }
    
    @GetMapping("/{candidateId}")
    public Result<HermesCandidate> getCandidate(@PathVariable String candidateId) {
        Optional<HermesCandidate> candidate = candidateService.getCandidate(candidateId);
        return candidate.map(Result::ok).orElse(Result.error("Candidate not found"));
    }
    
    @PostMapping("/{candidateId}/approve")
    public Result<String> approveCandidate(
        @PathVariable String candidateId,
        @RequestParam @NotBlank String reviewedBy
    ) {
        log.info("Approving candidate {} by {}", candidateId, reviewedBy);
        
        boolean success = candidateService.approveCandidate(candidateId, reviewedBy);
        
        if (success) {
            return Result.ok("Candidate approved successfully");
        } else {
            return Result.error("Failed to approve candidate (not found or not in PENDING status)");
        }
    }
    
    @PostMapping("/{candidateId}/reject")
    public Result<String> rejectCandidate(
        @PathVariable String candidateId,
        @RequestParam @NotBlank String reviewedBy
    ) {
        log.info("Rejecting candidate {} by {}", candidateId, reviewedBy);
        
        boolean success = candidateService.rejectCandidate(candidateId, reviewedBy);
        
        if (success) {
            return Result.ok("Candidate rejected successfully");
        } else {
            return Result.error("Failed to reject candidate (not found or not in PENDING status)");
        }
    }
}
