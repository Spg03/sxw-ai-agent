package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.eval.EvalCase;
import com.sxw.sxwaiagent.eval.EvalRepository;
import com.sxw.sxwaiagent.eval.EvalResult;
import com.sxw.sxwaiagent.eval.EvalRunner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/eval")
@Validated
public class EvalController {
    
    private static final Logger log = LoggerFactory.getLogger(EvalController.class);
    
    private final EvalRepository evalRepository;
    private final EvalRunner evalRunner;
    
    public EvalController(EvalRepository evalRepository, EvalRunner evalRunner) {
        this.evalRepository = evalRepository;
        this.evalRunner = evalRunner;
    }
    
    @GetMapping("/cases")
    public Result<List<EvalCase>> getAllCases() {
        List<EvalCase> cases = evalRepository.findAllCases();
        return Result.ok(cases);
    }
    
    @GetMapping("/cases/{caseId}")
    public Result<EvalCase> getCase(@PathVariable String caseId) {
        Optional<EvalCase> evalCase = evalRepository.findCaseById(caseId);
        return evalCase.map(Result::ok).orElse(Result.error("Eval case not found"));
    }
    
    @PostMapping("/cases")
    public Result<String> createCase(@RequestBody CreateCaseRequest request) {
        log.info("Creating eval case: {}", request.name());
        
        EvalCase evalCase = new EvalCase(
            null,
            request.name(),
            request.input(),
            request.expectedOutput(),
            request.tags(),
            Instant.now()
        );
        
        String caseId = evalRepository.saveCase(evalCase);
        return Result.ok(caseId);
    }
    
    @GetMapping("/cases/{caseId}/results")
    public Result<List<EvalResult>> getCaseResults(@PathVariable String caseId) {
        List<EvalResult> results = evalRepository.findResultsByCaseId(caseId);
        return Result.ok(results);
    }
    
    @PostMapping("/run")
    public Result<EvalRunner.EvalRunResult> runEval(
        @RequestParam @NotBlank String agentType,
        @RequestParam(required = false) List<String> caseIds
    ) {
        log.info("Running eval for agent: {} with {} cases", 
            agentType, caseIds != null ? caseIds.size() : "all");
        
        EvalRunner.EvalRunResult result;
        
        if (caseIds != null && !caseIds.isEmpty()) {
            result = evalRunner.runEval(agentType, caseIds);
        } else {
            result = evalRunner.runAllCases(agentType);
        }
        
        return Result.ok(result);
    }
    
    public record CreateCaseRequest(
        @NotBlank String name,
        @NotBlank String input,
        String expectedOutput,
        String tags
    ) {}
}
