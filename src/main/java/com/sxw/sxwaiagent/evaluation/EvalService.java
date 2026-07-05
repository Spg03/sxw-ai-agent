package com.sxw.sxwaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Eval service.
 * <p>
 * Provides eval case management, eval runs, result queries and other features.
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalExecutor evalExecutor;

    public EvalService(
        EvalCaseRepository evalCaseRepository,
        EvalRunRepository evalRunRepository,
        EvalExecutor evalExecutor
    ) {
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalExecutor = evalExecutor;
    }

    // ==================== 用例管理 ====================

    /**
     * 创建评测用例
     */
    public EvalCase createCase(
        String caseName,
        EvalCaseType caseType,
        String profileCode,
        String inputPrompt,
        String expectedOutput,
        String createdBy
    ) {
        String caseId = "eval-" + UUID.randomUUID().toString().substring(0, 8);

        EvalCase evalCase = new EvalCase(
            null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
            profileCode, inputPrompt, expectedOutput, null, null, 5,
            createdBy, LocalDateTime.now(), LocalDateTime.now()
        );

        evalCaseRepository.save(evalCase);
        log.info("Created eval case: {} - {}", caseId, caseName);
        return evalCase;
    }

    /**
     * 激活评测用例
     */
    public void activateCase(String caseId) {
        evalCaseRepository.updateStatus(caseId, EvalCaseStatus.ACTIVE);
        log.info("Activated eval case: {}", caseId);
    }

    /**
     * 禁用评测用例
     */
    public void disableCase(String caseId) {
        evalCaseRepository.updateStatus(caseId, EvalCaseStatus.DISABLED);
        log.info("Disabled eval case: {}", caseId);
    }

    /**
     * 查询用例
     */
    public Optional<EvalCase> findCase(String caseId) {
        return evalCaseRepository.findByCaseId(caseId);
    }

    /**
     * 查询所有活跃用例
     */
    public List<EvalCase> findAllActiveCases() {
        return evalCaseRepository.findAllActive();
    }

    /**
     * 按 Profile 查询用例
     */
    public List<EvalCase> findCasesByProfile(String profileCode) {
        return evalCaseRepository.findByProfileCode(profileCode);
    }

    // ==================== 评测运行 ====================

    /**
     * 创建评测运行
     */
    public EvalRun createRun(String runName, String profileCode, List<String> caseIds, String triggeredBy) {
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);

        EvalRun run = new EvalRun(runId, runName, profileCode, caseIds, triggeredBy);
        evalRunRepository.save(run);
        log.info("Created eval run: {} - {}", runId, runName);
        return run;
    }

    /**
     * 执行评测运行
     */
    public void executeRun(String runId) {
        Optional<EvalRun> optRun = evalRunRepository.findByRunId(runId);
        if (optRun.isEmpty()) {
            throw new IllegalArgumentException("Eval run not found: " + runId);
        }

        EvalRun run = optRun.get();
        if (!run.canStart()) {
            throw new IllegalStateException("Eval run cannot start: " + run.status());
        }

        // 更新状态为 RUNNING
        evalRunRepository.updateStatus(runId, EvalRunStatus.RUNNING);

        try {
            // 获取用例
            List<EvalCase> cases = evalCaseRepository.findByIds(run.caseIds());
            if (cases.isEmpty()) {
                throw new IllegalStateException("No active cases found for run: " + runId);
            }

            // 执行评测
            long startTime = System.currentTimeMillis();
            List<EvalResult> results = evalExecutor.executeBatch(cases);
            long durationMs = System.currentTimeMillis() - startTime;

            // 统计结果
            int passed = 0, failed = 0, skipped = 0;
            for (EvalResult result : results) {
                if (result.passed()) {
                    passed++;
                } else if (result.errorMessage() != null) {
                    failed++;
                } else {
                    failed++;
                }
            }
            skipped = cases.size() - results.size();

            double passRate = cases.size() > 0 ? (double) passed / cases.size() * 100.0 : 0.0;

            // 更新结果
            evalRunRepository.updateResults(runId, EvalRunStatus.COMPLETED, passed, failed, skipped, passRate, durationMs);
            log.info("Eval run completed: {} - {} passed, {} failed, {} skipped ({}ms)",
                runId, passed, failed, skipped, durationMs);

        } catch (Exception e) {
            log.error("Eval run failed: {} - {}", runId, e.getMessage(), e);
            evalRunRepository.updateError(runId, e.getMessage());
        }
    }

    /**
     * 查询评测运行
     */
    public Optional<EvalRun> findRun(String runId) {
        return evalRunRepository.findByRunId(runId);
    }

    /**
     * 查询最近的评测运行
     */
    public List<EvalRun> findRecentRuns(int limit) {
        return evalRunRepository.findRecent(limit);
    }

    /**
     * 按 Profile 查询评测运行
     */
    public List<EvalRun> findRunsByProfile(String profileCode) {
        return evalRunRepository.findByProfileCode(profileCode);
    }
}
