package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.evaluation.EvalCase;
import com.sxw.sxwaiagent.evaluation.EvalCaseType;
import com.sxw.sxwaiagent.evaluation.EvalResult;
import com.sxw.sxwaiagent.evaluation.EvalRun;
import com.sxw.sxwaiagent.evaluation.EvalService;
import com.sxw.sxwaiagent.evaluation.ValidationMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Tag(name = "评测管理", description = "评测用例管理、评测运行与结果查询")
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

    @Operation(summary = "获取所有评测用例")
    @GetMapping("/cases")
    public Result<List<EvalCase>> getAllCases() {
        return Result.ok(evalService.findAllCases());
    }

    @Operation(summary = "获取单个评测用例", description = "根据 caseId 查询评测用例详情")
    @GetMapping("/cases/{caseId}")
    public Result<EvalCase> getCase(@PathVariable String caseId) {
        Optional<EvalCase> evalCase = evalService.findCase(caseId);
        return evalCase.map(Result::ok).orElse(Result.error("Eval case not found"));
    }

    @Operation(summary = "创建评测用例", description = "创建新的评测用例，初始状态为 DRAFT")
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

    @Operation(summary = "删除评测用例")
    @DeleteMapping("/cases/{caseId}")
    public Result<String> deleteCase(@PathVariable String caseId) {
        log.info("Deleting eval case: {}", caseId);
        evalService.deleteCase(caseId);
        return Result.ok(caseId);
    }

    // ==================== Runs ====================

    @Operation(summary = "执行评测", description = "创建并立即执行一次评测运行，支持指定用例或全量活跃用例")
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

    @Operation(summary = "列出所有评测运行")
    @GetMapping("/runs")
    public Result<List<EvalRun>> listRuns() {
        return Result.ok(evalService.listRuns());
    }

    @Operation(summary = "获取评测运行详情")
    @GetMapping("/runs/{runId}")
    public Result<EvalRun> getRun(@PathVariable String runId) {
        Optional<EvalRun> run = evalService.findRun(runId);
        return run.map(Result::ok).orElse(Result.error("Eval run not found"));
    }

    @Operation(summary = "获取评测运行结果", description = "返回指定运行中每个用例的详细评测结果")
    @GetMapping("/runs/{runId}/results")
    public Result<List<EvalResult>> getRunResults(@PathVariable String runId) {
        return Result.ok(evalService.getRunResults(runId));
    }

    @Operation(summary = "导出评测报告", description = "生成 Markdown 格式的评测报告并下载")
    @GetMapping("/runs/{runId}/report")
    public ResponseEntity<byte[]> exportReport(@PathVariable String runId) {
        Optional<EvalRun> runOpt = evalService.findRun(runId);
        if (runOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EvalRun run = runOpt.get();
        List<EvalResult> results = evalService.getRunResults(runId);
        String markdown = buildMarkdownReport(run, results);

        String filename = String.format("eval-report-%s-%s.md",
            runId, run.startedAt() != null
                ? run.startedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                : "unknown");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
            .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建 Markdown 格式的评测报告
     */
    private String buildMarkdownReport(EvalRun run, List<EvalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 评测报告\n\n");
        sb.append("| 属性 | 值 |\n|---|---|\n");
        sb.append("| 运行 ID | ").append(run.runId()).append(" |\n");
        sb.append("| 名称 | ").append(run.runName()).append(" |\n");
        sb.append("| 状态 | ").append(run.status()).append(" |\n");
        sb.append("| Profile | ").append(run.profileCode()).append(" |\n");
        sb.append("| 通过率 | ").append(String.format("%.1f%%", run.calculatePassRate())).append(" |\n");
        sb.append("| 总用例 | ").append(run.totalCases()).append(" |\n");
        sb.append("| 通过 | ").append(run.passedCases()).append(" |\n");
        sb.append("| 失败 | ").append(run.failedCases()).append(" |\n");
        sb.append("| 耗时 | ").append(run.durationMs()).append(" ms |\n");
        sb.append("| 触发方式 | ").append(run.triggeredBy()).append(" |\n");
        if (run.startedAt() != null) {
            sb.append("| 开始时间 | ").append(run.startedAt()).append(" |\n");
        }
        sb.append("\n## 用例明细\n\n");
        sb.append("| # | 用例 | 结果 | 耗时(ms) | 详情 |\n");
        sb.append("|---|---|---|---|---|\n");

        for (int i = 0; i < results.size(); i++) {
            EvalResult r = results.get(i);
            String status = r.passed() ? "✅ PASS" : "❌ FAIL";
            String detail = r.errorMessage() != null ? r.errorMessage()
                : r.validationDetails() != null ? r.validationDetails()
                : (r.judgeReason() != null ? r.judgeReason() : "-");
            // 截断过长内容
            if (detail.length() > 80) {
                detail = detail.substring(0, 77) + "...";
            }
            sb.append("| ").append(i + 1)
              .append(" | ").append(r.caseName() != null ? r.caseName() : r.caseId())
              .append(" | ").append(status)
              .append(" | ").append(r.durationMs())
              .append(" | ").append(detail.replace("|", "\\|"))
              .append(" |\n");
        }

        sb.append("\n---\n");
        sb.append("*报告由 sxw-ai-agent Eval 系统自动生成*\n");
        return sb.toString();
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
