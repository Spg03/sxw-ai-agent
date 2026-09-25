package com.sxw.sxwaiagent.evaluation.comparison;

import com.sxw.sxwaiagent.evaluation.EvalCase;
import com.sxw.sxwaiagent.evaluation.EvalCaseRepository;
import com.sxw.sxwaiagent.evaluation.harness.EvalHarnessProperties;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalAdapterRegistry;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EvalComparisonService {
    private final EvalComparisonRepository repository;
    private final EvalCaseRepository caseRepository;
    private final HarnessEvalAdapterRegistry adapters;
    private final EvalHarnessProperties properties;

    public EvalComparisonService(EvalComparisonRepository repository, EvalCaseRepository caseRepository,
                                 HarnessEvalAdapterRegistry adapters, EvalHarnessProperties properties) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.adapters = adapters;
        this.properties = properties;
    }

    public EvalComparison create(String name, List<String> requestedCaseIds,
                                 List<HarnessTargetCode> requestedTargets, Integer requestedRepeats,
                                 String triggeredBy) {
        List<String> caseIds = requestedCaseIds == null || requestedCaseIds.isEmpty()
            ? caseRepository.findAllActive().stream().map(EvalCase::caseId).toList()
            : requestedCaseIds.stream().distinct().toList();
        if (caseIds.isEmpty()) throw new IllegalArgumentException("No evaluation cases selected");
        if (caseRepository.findByIds(caseIds).size() != caseIds.size()) {
            throw new IllegalArgumentException("One or more evaluation cases do not exist");
        }
        List<HarnessTargetCode> targets = requestedTargets == null || requestedTargets.isEmpty()
            ? List.of(HarnessTargetCode.LOCAL, HarnessTargetCode.DSH)
            : requestedTargets.stream().distinct().toList();
        targets.forEach(adapters::require);
        int repeats = requestedRepeats == null ? properties.repeats() : requestedRepeats;
        if (repeats < 1 || repeats > 20) throw new IllegalArgumentException("repeats must be between 1 and 20");
        String comparisonId = "cmp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        EvalComparison comparison = new EvalComparison(comparisonId,
            name == null || name.isBlank() ? "Harness A/B Comparison" : name,
            caseIds, targets, repeats, EvalComparisonStatus.QUEUED, 0, triggeredBy,
            null, null, null, java.time.LocalDateTime.now());
        repository.create(comparison);
        return repository.find(comparisonId).orElse(comparison);
    }

    public Optional<EvalComparison> find(String comparisonId) { return repository.find(comparisonId); }
    public List<EvalTargetResult> results(String comparisonId) {
        requireComparison(comparisonId);
        return repository.results(comparisonId);
    }

    public String report(String comparisonId) {
        EvalComparison comparison = requireComparison(comparisonId);
        List<EvalTargetResult> results = repository.results(comparisonId);
        StringBuilder out = new StringBuilder("# Harness 对比评测报告\n\n");
        out.append("- Comparison: `").append(comparison.comparisonId()).append("`\n")
            .append("- 状态: ").append(comparison.status()).append("\n")
            .append("- 重复次数: ").append(comparison.repeats()).append("\n")
            .append("- 目标: ").append(comparison.targets()).append("\n\n");
        out.append("| Target | Runs | Pass Rate | Avg Latency | P95 Latency | Latency σ | Avg Tokens | Tool Success | Security Violations |\n")
            .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (HarnessTargetCode target : comparison.targets()) {
            List<EvalTargetResult> group = results.stream().filter(r -> r.targetCode() == target).toList();
            if (group.isEmpty()) {
                out.append("| ").append(target).append(" | 0 | - | - | - | - | - | - | - |\n");
                continue;
            }
            long passed = group.stream().filter(EvalTargetResult::passed).count();
            double avgLatency = group.stream().mapToLong(EvalTargetResult::latencyMs).average().orElse(0);
            double latencyVariance = group.stream()
                .mapToDouble(result -> Math.pow(result.latencyMs() - avgLatency, 2))
                .average().orElse(0);
            double latencyStdDev = Math.sqrt(latencyVariance);
            List<Long> sortedLatency = group.stream().map(EvalTargetResult::latencyMs).sorted().toList();
            long p95 = sortedLatency.get(Math.min(sortedLatency.size() - 1,
                (int) Math.ceil(sortedLatency.size() * 0.95) - 1));
            double avgTokens = group.stream().mapToInt(r -> safe(r.inputTokens()) + safe(r.outputTokens())).average().orElse(0);
            double toolSuccess = group.stream().mapToDouble(EvalTargetResult::toolSuccessRate).average().orElse(0);
            int violations = group.stream().mapToInt(EvalTargetResult::securityViolationCount).sum();
            out.append(String.format("| %s | %d | %.1f%% | %.0f ms | %d ms | %.0f ms | %.0f | %.1f%% | %d |%n",
                target, group.size(), passed * 100.0 / group.size(), avgLatency, p95, latencyStdDev, avgTokens,
                toolSuccess * 100.0, violations));
        }
        out.append("\n## 明细\n\n")
            .append("| Case | Target | Repeat | Passed | Stop | Tokens | Latency | Error |\n")
            .append("|---|---|---:|---|---|---:|---:|---|\n");
        for (EvalTargetResult result : results) {
            out.append("| ").append(escape(result.caseName() == null ? result.caseId() : result.caseName()))
                .append(" | ").append(result.targetCode()).append(" | ").append(result.repeatIndex())
                .append(" | ").append(result.passed() ? "PASS" : "FAIL")
                .append(" | ").append(escape(result.stopReason()))
                .append(" | ").append(safe(result.inputTokens()) + safe(result.outputTokens()))
                .append(" | ").append(result.latencyMs()).append(" ms")
                .append(" | ").append(escape(result.errorCategory())).append(" |\n");
        }
        out.append("\n> Judge 使用独立模型；目标名称不进入 Judge 输入。Prompt/config hash 用于差异归因。\n");
        return out.toString();
    }

    private EvalComparison requireComparison(String id) {
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + id));
    }
    private static int safe(Integer value) { return value == null ? 0 : value; }
    private static String escape(String value) { return value == null ? "-" : value.replace("|", "\\|").replace("\n", " "); }
}
