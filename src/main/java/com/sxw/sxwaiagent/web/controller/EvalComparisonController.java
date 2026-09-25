package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.evaluation.comparison.EvalComparison;
import com.sxw.sxwaiagent.evaluation.comparison.EvalComparisonService;
import com.sxw.sxwaiagent.evaluation.comparison.EvalTargetResult;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Harness 对比评测", description = "AgentForge LOCAL 与 DSH Sidecar 离线 A/B 评测")
@RestController
@RequestMapping("/api/eval/comparisons")
public class EvalComparisonController {
    private final EvalComparisonService service;

    public EvalComparisonController(EvalComparisonService service) { this.service = service; }

    @Operation(summary = "创建异步 Harness 对比评测")
    @PostMapping
    public Result<EvalComparison> create(Authentication authentication, @Valid @RequestBody CreateRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.ok(service.create(request.name(), request.caseIds(), request.targets(), request.repeats(),
            user.username()));
    }

    @Operation(summary = "查询对比评测状态")
    @GetMapping("/{comparisonId}")
    public Result<EvalComparison> get(@PathVariable String comparisonId) {
        return service.find(comparisonId).map(Result::ok).orElse(Result.error("Comparison not found"));
    }

    @Operation(summary = "查询 LOCAL/DSH 配对结果")
    @GetMapping("/{comparisonId}/results")
    public Result<List<EvalTargetResult>> results(@PathVariable String comparisonId) {
        return Result.ok(service.results(comparisonId));
    }

    @Operation(summary = "导出 Harness 对比 Markdown 报告")
    @GetMapping("/{comparisonId}/report")
    public ResponseEntity<byte[]> report(@PathVariable String comparisonId) {
        byte[] body = service.report(comparisonId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=eval-comparison-" + comparisonId + ".md")
            .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
            .body(body);
    }

    public record CreateRequest(
        String name,
        List<String> caseIds,
        List<HarnessTargetCode> targets,
        @Min(1) @Max(20) Integer repeats
    ) { }
}
