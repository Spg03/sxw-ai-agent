package com.sxw.sxwaiagent.infrastructure.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.evaluation.EvaluationResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不依赖 LLM、不依赖 Spring 的快速单测：覆盖 judge 输出的 JSON 解析与降级。
 */
class LlmJudgeEvaluatorParseTest {

    @Test
    void parses_strict_json() {
        EvaluationResponse r = LlmJudgeEvaluator.parseJudgeResponse(
                "{\"pass\":true,\"score\":0.85,\"reason\":\"共情到位\"}");
        assertTrue(r.isPass());
        assertEquals(0.85f, r.getScore(), 0.001);
        assertEquals("共情到位", r.getFeedback());
    }

    @Test
    void parses_json_wrapped_in_markdown_fences() {
        String raw = """
                ```json
                {"pass": false, "score": 0.2, "reason": "缺少分点"}
                ```""";
        EvaluationResponse r = LlmJudgeEvaluator.parseJudgeResponse(raw);
        assertFalse(r.isPass());
        assertEquals(0.2f, r.getScore(), 0.001);
        assertEquals("缺少分点", r.getFeedback());
    }

    @Test
    void unparseable_raw_falls_back_to_fail() {
        EvaluationResponse r = LlmJudgeEvaluator.parseJudgeResponse("totally not json");
        assertFalse(r.isPass());
        assertEquals(0f, r.getScore());
        assertTrue(r.getFeedback().startsWith("judge response not parseable"));
    }

    @Test
    void empty_input_falls_back_to_fail() {
        EvaluationResponse r = LlmJudgeEvaluator.parseJudgeResponse(null);
        assertFalse(r.isPass());
    }

    @Test
    void score_defaults_when_missing() {
        EvaluationResponse passOnly = LlmJudgeEvaluator.parseJudgeResponse(
                "{\"pass\":true,\"reason\":\"ok\"}");
        assertTrue(passOnly.isPass());
        assertEquals(1.0f, passOnly.getScore(), 0.001);
    }
}
