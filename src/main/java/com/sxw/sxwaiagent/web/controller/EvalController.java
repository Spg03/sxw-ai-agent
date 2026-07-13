package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.evaluation.EvalCase;
import com.sxw.sxwaiagent.evaluation.EvalCaseType;
import com.sxw.sxwaiagent.evaluation.EvalResult;
import com.sxw.sxwaiagent.evaluation.EvalRun;
import com.sxw.sxwaiagent.evaluation.EvalService;
import com.sxw.sxwaiagent.evaluation.ValidationMode;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/eval")
@Validated
public class EvalController {

    private static final Logger log = LoggerFactory.getLogger(EvalController.class);

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    // ==================== Cases ====================

    @GetMapping("/cases")
    public Result<List<EvalCase>> getAllCases() {
        return Result.ok(evalService.findAllCases());
    }

    @GetMapping("/cases/{caseId}")
    public Result<EvalCase> getCase(@PathVariable String caseId) {
        Optional<EvalCase> evalCase = evalService.findCase(caseId);
        return evalCase.map(Result::ok).orElse(Result.error("Eval case not found"));
    }

    @PostMapping("/cases")
    public Result<EvalCase> createCase(@RequestBody CreateCaseRequest request) {
        log.info("Creating eval case: {}", request.name());

        EvalCaseType caseType = request.caseType() != null
            ? EvalCaseType.valueOf(request.caseType())
            : EvalCaseType.CONVERSATION;

        ValidationMode mode = request.validationMode() != null
            ? ValidationMode.valueOf(request.validationMode())
            : ValidationMode.KEYWORD_ONLY;

        EvalCase evalCase = evalService.createCase(
            request.name(),
            caseType,
            request.profileCode() != null ? request.profileCode() : "GENERAL",
            request.input(),
            request.expectedOutput(),
            request.judgeCriteria(),
            mode,
            null
        );
        return Result.ok(evalCase);
    }

    @DeleteMapping("/cases/{caseId}")
    public Result<String> deleteCase(@PathVariable String caseId) {
        log.info("Deleting eval case: {}", caseId);
        evalService.deleteCase(caseId);
        return Result.ok(caseId);
    }

    // ==================== Runs ====================

    @PostMapping("/run")
    public Result<EvalRun> runEval(@RequestBody RunEvalRequest request) {
        log.info("Running eval: {} with {} cases",
            request.name(), request.caseIds() != null ? request.caseIds().size() : "all");

        String profileCode = request.profileCode() != null ? request.profileCode() : "GENERAL";

        List<String> caseIds = request.caseIds();
        if (caseIds == null || caseIds.isEmpty()) {
            caseIds = evalService.findAllActiveCases().stream()
                .map(EvalCase::caseId)
                .toList();
        }

        EvalRun run = evalService.createRun(
            request.name() != null ? request.name() : "Manual Run",
            profileCode,
            caseIds,
            "api"
        );

        evalService.executeRun(run.runId());

        return evalService.findRun(run.runId())
            .map(Result::ok)
            .orElse(Result.ok(run));
    }

    @GetMapping("/runs")
    public Result<List<EvalRun>> listRuns() {
        return Result.ok(evalService.listRuns());
    }

    @GetMapping("/runs/{runId}")
    public Result<EvalRun> getRun(@PathVariable String runId) {
        Optional<EvalRun> run = evalService.findRun(runId);
        return run.map(Result::ok).orElse(Result.error("Eval run not found"));
    }

    @GetMapping("/runs/{runId}/results")
    public Result<List<EvalResult>> getRunResults(@PathVariable String runId) {
        return Result.ok(evalService.getRunResults(runId));
    }

    // ==================== DTOs ====================

    public record CreateCaseRequest(
        @NotBlank String name,
        @NotBlank String input,
        String expectedOutput,
        String caseType,
        String profileCode,
        String tags,
        String judgeCriteria,
        String validationMode
    ) {}

    public record RunEvalRequest(
        String name,
        String profileCode,
        List<String> caseIds
    ) {}
}
