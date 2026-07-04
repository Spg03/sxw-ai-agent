package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.evaluation.EvalCase;
import com.sxw.sxwaiagent.evaluation.EvalRun;
import com.sxw.sxwaiagent.evaluation.EvalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测系统控制器
 * <p>
 * 提供评测用例管理和评测运行控制的 REST API。
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    // ==================== 用例管理 ====================

    /**
     * 创建评测用例
     */
    @PostMapping("/cases")
    public ResponseEntity<EvalCase> createCase(@RequestBody CreateCaseRequest request) {
        EvalCase evalCase = evalService.createCase(
            request.caseName(),
            request.caseType(),
            request.profileCode(),
            request.inputPrompt(),
            request.expectedOutput(),
            request.createdBy()
        );
        return ResponseEntity.ok(evalCase);
    }

    /**
     * 激活评测用例
     */
    @PostMapping("/cases/{caseId}/activate")
    public ResponseEntity<Void> activateCase(@PathVariable String caseId) {
        evalService.activateCase(caseId);
        return ResponseEntity.ok().build();
    }

    /**
     * 禁用评测用例
     */
    @PostMapping("/cases/{caseId}/disable")
    public ResponseEntity<Void> disableCase(@PathVariable String caseId) {
        evalService.disableCase(caseId);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询评测用例
     */
    @GetMapping("/cases/{caseId}")
    public ResponseEntity<EvalCase> getCase(@PathVariable String caseId) {
        return evalService.findCase(caseId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询所有活跃用例
     */
    @GetMapping("/cases/active")
    public ResponseEntity<List<EvalCase>> getAllActiveCases() {
        return ResponseEntity.ok(evalService.findAllActiveCases());
    }

    /**
     * 按 Profile 查询用例
     */
    @GetMapping("/cases/profile/{profileCode}")
    public ResponseEntity<List<EvalCase>> getCasesByProfile(@PathVariable String profileCode) {
        return ResponseEntity.ok(evalService.findCasesByProfile(profileCode));
    }

    // ==================== 评测运行 ====================

    /**
     * 创建评测运行
     */
    @PostMapping("/runs")
    public ResponseEntity<EvalRun> createRun(@RequestBody CreateRunRequest request) {
        EvalRun run = evalService.createRun(
            request.runName(),
            request.profileCode(),
            request.caseIds(),
            request.triggeredBy()
        );
        return ResponseEntity.ok(run);
    }

    /**
     * 执行评测运行
     */
    @PostMapping("/runs/{runId}/execute")
    public ResponseEntity<Void> executeRun(@PathVariable String runId) {
        evalService.executeRun(runId);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询评测运行
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<EvalRun> getRun(@PathVariable String runId) {
        return evalService.findRun(runId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询最近的评测运行
     */
    @GetMapping("/runs/recent")
    public ResponseEntity<List<EvalRun>> getRecentRuns(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(evalService.findRecentRuns(limit));
    }

    /**
     * 按 Profile 查询评测运行
     */
    @GetMapping("/runs/profile/{profileCode}")
    public ResponseEntity<List<EvalRun>> getRunsByProfile(@PathVariable String profileCode) {
        return ResponseEntity.ok(evalService.findRunsByProfile(profileCode));
    }

    // ==================== 请求 DTO ====================

    public record CreateCaseRequest(
        String caseName,
        com.sxw.sxwaiagent.evaluation.EvalCaseType caseType,
        String profileCode,
        String inputPrompt,
        String expectedOutput,
        String createdBy
    ) {}

    public record CreateRunRequest(
        String runName,
        String profileCode,
        List<String> caseIds,
        String triggeredBy
    ) {}
}
