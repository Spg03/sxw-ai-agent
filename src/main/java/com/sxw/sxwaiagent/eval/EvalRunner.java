package com.sxw.sxwaiagent.eval;

import com.sxw.sxwaiagent.agent.dto.AgentRequest;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.orchestrator.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvalRunner {
    
    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    
    private final EvalRepository evalRepository;
    private final AgentOrchestrator agentOrchestrator;
    
    public EvalRunner(EvalRepository evalRepository, AgentOrchestrator agentOrchestrator) {
        this.evalRepository = evalRepository;
        this.agentOrchestrator = agentOrchestrator;
    }
    
    public EvalRunResult runEval(String agentType, List<String> caseIds) {
        log.info("Starting eval run for agent {} with {} cases", agentType, caseIds.size());
        
        int passed = 0;
        int failed = 0;
        
        for (String caseId : caseIds) {
            try {
                var evalCaseOpt = evalRepository.findCaseById(caseId);
                if (evalCaseOpt.isEmpty()) {
                    log.warn("Eval case not found: {}", caseId);
                    failed++;
                    continue;
                }
                
                EvalCase evalCase = evalCaseOpt.get();
                
                // Build request
                AgentRequest request = AgentRequest.builder()
                    .chatId("eval_" + caseId)
                    .message(evalCase.input())
                    .build();
                
                // Execute agent
                long startTime = System.currentTimeMillis();
                AgentResponse response = agentOrchestrator.execute(request, agentType);
                long latencyMs = System.currentTimeMillis() - startTime;
                
                // Compare output
                boolean match = compareOutput(response.answer(), evalCase.expectedOutput());
                
                // Save result
                EvalResult result = new EvalResult(
                    UUID.randomUUID().toString(),
                    caseId,
                    agentType,
                    response.answer(),
                    match,
                    latencyMs,
                    match ? "Output matched expected" : "Output did not match expected",
                    Instant.now()
                );
                
                evalRepository.saveResult(result);
                
                if (match) {
                    passed++;
                } else {
                    failed++;
                }
                
            } catch (Exception e) {
                log.error("Failed to run eval case {}: {}", caseId, e.getMessage());
                failed++;
            }
        }
        
        double passRate = caseIds.isEmpty() ? 0.0 : (passed * 100.0 / caseIds.size());
        
        log.info("Eval run completed: {}/{} passed ({}%)", passed, caseIds.size(), passRate);
        
        return new EvalRunResult(
            caseIds.size(),
            passed,
            failed,
            passRate
        );
    }
    
    public EvalRunResult runAllCases(String agentType) {
        List<EvalCase> allCases = evalRepository.findAllCases();
        List<String> caseIds = allCases.stream()
            .map(EvalCase::caseId)
            .collect(Collectors.toList());
        
        return runEval(agentType, caseIds);
    }
    
    private boolean compareOutput(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true; // No expected output, always pass
        }
        
        if (actual == null) {
            return false;
        }
        
        // Simple substring match for now
        // TODO: Implement more sophisticated comparison (semantic similarity, etc.)
        return actual.contains(expected) || expected.contains(actual);
    }
    
    public record EvalRunResult(
        int totalCases,
        int passed,
        int failed,
        double passRate
    ) {}
}
