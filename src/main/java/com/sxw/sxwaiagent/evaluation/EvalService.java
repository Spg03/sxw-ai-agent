package com.sxw.sxwaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalResultRepository evalResultRepository;
    private final EvalExecutor evalExecutor;

    public EvalService(
        EvalCaseRepository evalCaseRepository,
        EvalRunRepository evalRunRepository,
        EvalResultRepository evalResultRepository,
        EvalExecutor evalExecutor
    ) {
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalResultRepository = evalResultRepository;
        this.evalExecutor = evalExecutor;
    }

    // ==================== Case Management ====================

    public EvalCase createCase(
        String caseName,
        EvalCaseType caseType,
        String profileCode,
        String inputPrompt,
        String expectedOutput,
        String judgeCriteria,
        ValidationMode validationMode,
        String createdBy
    ) {
        String caseId = "eval-" + UUID.randomUUID().toString().substring(0, 8);

        EvalCase evalCase = new EvalCase(
            null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
            profileCode, inputPrompt, expectedOutput, null,
            judgeCriteria, validationMode != null ? validationMode : ValidationMode.KEYWORD_ONLY,
            null, 5, createdBy, LocalDateTime.now(), LocalDateTime.now()
        );

        evalCaseRepository.save(evalCase);
        log.info("Created eval case: {} - {}", caseId, caseName);
        return evalCase;
    }

    public void activateCase(String caseId) {
        evalCaseRepository.updateStatus(caseId, EvalCaseStatus.ACTIVE);
        log.info("Activated eval case: {}", caseId);
    }

    public void disableCase(String caseId) {
        evalCaseRepository.updateStatus(caseId, EvalCaseStatus.DISABLED);
        log.info("Disabled eval case: {}", caseId);
    }

    public Optional<EvalCase> findCase(String caseId) {
        return evalCaseRepository.findByCaseId(caseId);
    }

    public List<EvalCase> findAllCases() {
        return evalCaseRepository.findAll();
    }

    public List<EvalCase> findAllActiveCases() {
        return evalCaseRepository.findAllActive();
    }

    public List<EvalCase> findCasesByProfile(String profileCode) {
        return evalCaseRepository.findByProfileCode(profileCode);
    }

    public void deleteCase(String caseId) {
        evalCaseRepository.deleteByCaseId(caseId);
        log.info("Deleted eval case: {}", caseId);
    }

    // ==================== Run Management ====================

    public EvalRun createRun(String runName, String profileCode, List<String> caseIds, String triggeredBy) {
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        EvalRun run = new EvalRun(runId, runName, profileCode, caseIds, triggeredBy);
        evalRunRepository.save(run);
        log.info("Created eval run: {} - {}", runId, runName);
        return run;
    }

    public void executeRun(String runId) {
        Optional<EvalRun> optRun = evalRunRepository.findByRunId(runId);
        if (optRun.isEmpty()) {
            throw new IllegalArgumentException("Eval run not found: " + runId);
        }

        EvalRun run = optRun.get();
        if (!run.canStart()) {
            throw new IllegalStateException("Eval run cannot start: " + run.status());
        }

        evalRunRepository.updateStatus(runId, EvalRunStatus.RUNNING);

        try {
            List<EvalCase> cases = evalCaseRepository.findByIds(run.caseIds());
            if (cases.isEmpty()) {
                throw new IllegalStateException("No active cases found for run: " + runId);
            }

            long startTime = System.currentTimeMillis();
            List<EvalResult> results = evalExecutor.executeBatch(cases);
            long durationMs = System.currentTimeMillis() - startTime;

            // Persist individual results
            evalResultRepository.saveBatch(runId, results);

            // Compute summary
            int passed = 0, failed = 0;
            for (EvalResult r : results) {
                if (r.passed()) passed++; else failed++;
            }
            int skipped = cases.size() - results.size();
            double passRate = cases.size() > 0 ? (double) passed / cases.size() * 100.0 : 0.0;

            evalRunRepository.updateResults(runId, EvalRunStatus.COMPLETED,
                passed, failed, skipped, passRate, durationMs);
            log.info("Eval run completed: {} - {} passed, {} failed ({}ms)",
                runId, passed, failed, durationMs);

        } catch (Exception e) {
            log.error("Eval run failed: {}", runId, e);
            evalRunRepository.updateError(runId, e.getMessage());
        }
    }

    public List<EvalResult> getRunResults(String runId) {
        return evalResultRepository.findByRunId(runId);
    }

    public Optional<EvalRun> findRun(String runId) {
        return evalRunRepository.findByRunId(runId);
    }

    public List<EvalRun> listRuns() {
        return evalRunRepository.findAll();
    }

    public List<EvalRun> findRecentRuns(int limit) {
        return evalRunRepository.findRecent(limit);
    }

    public List<EvalRun> findRunsByProfile(String profileCode) {
        return evalRunRepository.findByProfileCode(profileCode);
    }
}
