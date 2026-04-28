package com.sxw.sxwaiagent.infrastructure.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把一组 {@link CaseResult} 序列化成 Markdown 报告，便于贴到 README / PR description。
 * <p>
 * 输出包含：
 * <ul>
 *   <li>顶层汇总（pass-rate、avg latency、按 category 的 pass-rate）</li>
 *   <li>每条用例的 input / actual / 各评估器子结果</li>
 * </ul>
 */
public record EvalReport(List<CaseResult> results, Duration totalDuration) {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Path writeMarkdown(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.writeString(out, toMarkdown(), StandardCharsets.UTF_8);
        return out;
    }

    public String toMarkdown() {
        int total = results.size();
        long passed = results.stream().filter(CaseResult::overallPassed).count();
        double passRate = total == 0 ? 0 : passed * 100.0 / total;
        double avgLatency = results.stream().mapToLong(CaseResult::latencyMs).average().orElse(0);

        Map<String, long[]> byCat = new LinkedHashMap<>(); // [pass, total]
        for (CaseResult r : results) {
            long[] arr = byCat.computeIfAbsent(r.category(), k -> new long[2]);
            arr[1]++;
            if (r.overallPassed()) arr[0]++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Eval Report\n\n");
        sb.append("- **Generated**: ").append(LocalDateTime.now().format(TS)).append('\n');
        sb.append("- **Total cases**: ").append(total).append('\n');
        sb.append("- **Passed**: ").append(passed).append(" (")
                .append(String.format("%.1f%%", passRate)).append(")\n");
        sb.append("- **Avg latency**: ").append(String.format("%.0f ms", avgLatency)).append('\n');
        sb.append("- **Total duration**: ").append(totalDuration.toSeconds()).append(" s\n\n");

        sb.append("## Pass rate by category\n\n");
        sb.append("| Category | Passed | Total | Rate |\n|---|---|---|---|\n");
        for (Map.Entry<String, long[]> e : byCat.entrySet()) {
            long p = e.getValue()[0], t = e.getValue()[1];
            sb.append("| ").append(e.getKey())
                    .append(" | ").append(p)
                    .append(" | ").append(t)
                    .append(" | ").append(String.format("%.0f%%", t == 0 ? 0 : p * 100.0 / t))
                    .append(" |\n");
        }

        sb.append("\n## Cases\n\n");
        for (CaseResult r : results) {
            sb.append("### ").append(r.overallPassed() ? "✅" : "❌")
                    .append(" `").append(r.caseId()).append("` (")
                    .append(r.category()).append(", ").append(r.latencyMs()).append(" ms)\n\n");
            sb.append("**Input**: ").append(escapeInline(r.input())).append("\n\n");
            sb.append("**Actual**:\n\n").append("```\n").append(truncate(r.actual(), 1000)).append("\n```\n\n");
            sb.append("| Check | Pass | Score | Reason |\n|---|---|---|---|\n");
            for (Map.Entry<String, CaseResult.Check> e : r.checks().entrySet()) {
                CaseResult.Check c = e.getValue();
                sb.append("| ").append(e.getKey())
                        .append(" | ").append(c.passed() ? "✅" : "❌")
                        .append(" | ").append(String.format("%.2f", c.score()))
                        .append(" | ").append(escapeInline(c.reason()))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 简明文本汇总，CLI / 日志友好。 */
    public String toSummary() {
        long passed = results.stream().filter(CaseResult::overallPassed).count();
        double rate = results.isEmpty() ? 0 : passed * 100.0 / results.size();
        String byCat = results.stream().collect(Collectors.groupingBy(CaseResult::category)).entrySet().stream()
                .map(e -> {
                    long p = e.getValue().stream().filter(CaseResult::overallPassed).count();
                    return e.getKey() + ":" + p + "/" + e.getValue().size();
                })
                .collect(Collectors.joining(", "));
        return String.format("Eval done: %d/%d passed (%.1f%%) in %ds — by category: %s",
                passed, results.size(), rate, totalDuration.toSeconds(), byCat);
    }

    private static String escapeInline(String s) {
        return s == null ? "" : s.replace("\n", " ").replace("|", "\\|");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n…(truncated)";
    }
}
