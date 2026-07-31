package com.sxw.sxwaiagent.infrastructure.eval;

import com.sxw.sxwaiagent.love.LoveApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 评测编排器：
 * <ol>
 *   <li>从 classpath 加载用例（{@link EvalDataset}）</li>
 *   <li>对每条用例调用被测系统（默认 {@link LoveApp}），测量延迟，捕获回复</li>
 *   <li>跑两类评估器：{@link KeywordContainsEvaluator}（确定性）+ {@link LlmJudgeEvaluator}（LLM-as-Judge）</li>
 *   <li>汇总成 {@link EvalReport}，可序列化为 markdown</li>
 * </ol>
 *
 * <p>使用方式：
 * <pre>
 *   EvalReport report = evalRunner.run("classpath:eval/love-app.yaml");
 *   System.out.println(report.toSummary());
 *   report.writeMarkdown(Path.of("target/eval-report.md"));
 * </pre>
 */
@Component
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final LoveApp loveApp;
    private final LlmJudgeEvaluator judge;

    public EvalRunner(LoveApp loveApp, ChatModel chatModel) {
        this.loveApp = loveApp;
        this.judge = new LlmJudgeEvaluator(chatModel);
    }

    /** 默认目标：LoveApp（每条用例独立 chatId，避免上下文污染）。 */
    public EvalReport run(String classpathPattern) {
        return run(classpathPattern, input -> loveApp.doChat(input, "eval-" + UUID.randomUUID()));
    }

    /** 自定义被测系统：传入 input → output 函数。 */
    public EvalReport run(String classpathPattern, Function<String, String> target) {
        List<EvalCase> cases = EvalDataset.load(classpathPattern);
        log.info("Eval start: {} cases from {}", cases.size(), classpathPattern);
        Instant t0 = Instant.now();
        List<CaseResult> results = new ArrayList<>(cases.size());
        for (EvalCase c : cases) {
            results.add(runOne(c, target));
        }
        EvalReport report = new EvalReport(results, Duration.between(t0, Instant.now()));
        log.info(report.toSummary());
        return report;
    }

    // ---------- internals ----------

    private CaseResult runOne(EvalCase c, Function<String, String> target) {
        Instant start = Instant.now();
        String actual;
        try {
            actual = target.apply(c.input());
        } catch (Exception e) {
            actual = "EXCEPTION: " + e.getMessage();
        }
        long latencyMs = Duration.between(start, Instant.now()).toMillis();

        Map<String, CaseResult.Check> checks = new LinkedHashMap<>();
        checks.put(KeywordContainsEvaluator.NAME, KeywordContainsEvaluator.evaluate(c, actual));
        checks.put(LlmJudgeEvaluator.NAME, judge.evaluateCase(c, actual));
        if (c.maxLatencyMs() > 0) {
            boolean ok = latencyMs <= c.maxLatencyMs();
            checks.put("latency",
                    new CaseResult.Check(ok, ok ? 1 : 0,
                            latencyMs + " ms vs limit " + c.maxLatencyMs() + " ms"));
        }
        CaseResult r = CaseResult.of(c, actual, latencyMs, checks);
        log.info("  case {} -> {} ({} ms)", c.id(), r.overallPassed() ? "PASS" : "FAIL", latencyMs);
        return r;
    }
}
