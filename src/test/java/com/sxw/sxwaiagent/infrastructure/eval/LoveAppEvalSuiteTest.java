package com.sxw.sxwaiagent.infrastructure.eval;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoveApp 端到端评测套件。
 * <p>
 * 默认 <b>不</b> 在 {@code mvn test} 中运行（surefire 已排除 {@code eval} 标签），
 * 避免误烧 token。手动运行：
 * <pre>
 *   mvn -Dgroups=eval test -Dtest=LoveAppEvalSuiteTest
 *   # 或在 IDE 直接 run 本类
 * </pre>
 * 报告输出到 {@code target/eval-report.md}。
 *
 * <p>需要环境变量 {@code DASHSCOPE_API_KEY}。
 */
@SpringBootTest
@Tag("eval")
class LoveAppEvalSuiteTest {

    @Resource
    private EvalRunner evalRunner;

    @Test
    void run_love_app_eval_suite() throws Exception {
        EvalReport report = evalRunner.run("classpath:eval/love-app.yaml");
        Path out = Path.of("target", "eval-report.md");
        report.writeMarkdown(out);

        System.out.println("\n========================================");
        System.out.println(report.toSummary());
        System.out.println("Markdown report: " + out.toAbsolutePath());
        System.out.println("========================================\n");

        // 60% 通过率作为 CI 守门阈值（demo 用，生产可调高）
        long passed = report.results().stream().filter(CaseResult::overallPassed).count();
        double rate = report.results().isEmpty() ? 0 : passed * 100.0 / report.results().size();
        assertTrue(rate >= 60.0,
                String.format("Eval pass-rate %.1f%% below threshold 60%%, see %s", rate, out));
    }
}
