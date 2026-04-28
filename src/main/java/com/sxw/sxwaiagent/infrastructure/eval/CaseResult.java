package com.sxw.sxwaiagent.infrastructure.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个 {@link EvalCase} 的执行 + 评估结果。
 *
 * @param caseId        用例 id
 * @param category      用例类别
 * @param input         原始输入
 * @param actual        被测系统的实际回复
 * @param latencyMs     端到端延迟
 * @param checks        每个评估器的子结果（key = 评估器名，value = pass/score/reason）
 * @param overallPassed 全部子检查通过才为 true
 */
public record CaseResult(
        String caseId,
        String category,
        String input,
        String actual,
        long latencyMs,
        Map<String, Check> checks,
        boolean overallPassed
) {
    public record Check(boolean passed, double score, String reason) {}

    public static CaseResult of(EvalCase c, String actual, long latencyMs, Map<String, Check> checks) {
        boolean all = !checks.isEmpty() && checks.values().stream().allMatch(Check::passed);
        return new CaseResult(c.id(), c.category(), c.input(), actual, latencyMs,
                new LinkedHashMap<>(checks), all);
    }
}
