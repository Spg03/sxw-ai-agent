package com.sxw.sxwaiagent.infrastructure.eval;

import java.util.List;
import java.util.Locale;

/**
 * 确定性评估器：检查 actual 中是否命中关键词。
 * <p>
 * 优先用 {@link EvalCase#expectKeywordsAll()}（全部命中）；若为空，再看
 * {@link EvalCase#expectKeywordsAny()}（任一命中）。两者皆空则返回一个 "skip" 结果，
 * 不计入失败。匹配大小写不敏感。
 */
public final class KeywordContainsEvaluator {

    public static final String NAME = "keyword";

    private KeywordContainsEvaluator() {}

    public static CaseResult.Check evaluate(EvalCase c, String actual) {
        String haystack = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        List<String> all = c.expectKeywordsAll();
        if (all != null && !all.isEmpty()) {
            for (String kw : all) {
                if (!haystack.contains(kw.toLowerCase(Locale.ROOT))) {
                    return new CaseResult.Check(false, 0, "missing required keyword: " + kw);
                }
            }
            return new CaseResult.Check(true, 1, "all required keywords present");
        }
        List<String> any = c.expectKeywordsAny();
        if (any != null && !any.isEmpty()) {
            for (String kw : any) {
                if (haystack.contains(kw.toLowerCase(Locale.ROOT))) {
                    return new CaseResult.Check(true, 1, "matched keyword: " + kw);
                }
            }
            return new CaseResult.Check(false, 0, "none of expected keywords matched: " + any);
        }
        return new CaseResult.Check(true, 1, "skipped (no keywords configured)");
    }
}
