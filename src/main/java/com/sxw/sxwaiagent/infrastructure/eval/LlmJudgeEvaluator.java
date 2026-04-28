package com.sxw.sxwaiagent.infrastructure.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM-as-Judge 评估器，使用项目内已配置的 ChatModel 作为 judge，输出严格 JSON：
 * <pre>{"pass": true/false, "score": 0.0~1.0, "reason": "..."}</pre>
 * 同时实现 Spring AI 的 {@link Evaluator} 契约，可与 {@code RelevancyEvaluator}
 * / {@code FactCheckingEvaluator} 互换使用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>system prompt 强约束输出 JSON，简化解析失败时降级为 fail with reason</li>
 *   <li>不带工具、不带历史 — 单轮判定，避免 judge 自身被 prompt-injection</li>
 *   <li>评估 {@link EvalCase} 时通过 {@link #evaluateCase} 直接传 criteria；
 *       走 Spring AI 接口时则把 {@link EvaluationRequest#getUserText()} 当作 criteria。</li>
 * </ul>
 */
public class LlmJudgeEvaluator implements Evaluator {

    public static final String NAME = "llm-judge";
    private static final Logger log = LoggerFactory.getLogger(LlmJudgeEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a strict and impartial evaluator for an AI assistant's answer.
            You will be given:
              - The original user input
              - The judging criteria
              - The actual answer produced by the assistant
            You must respond with a SINGLE JSON object and nothing else, exactly in this shape:
              {"pass": <true|false>, "score": <number between 0 and 1>, "reason": "<short Chinese reason, <=80 chars>"}
            No markdown, no code fences, no extra commentary.
            "pass" is true only if the answer satisfies ALL stated criteria.
            "score" reflects degree of compliance (1.0 = perfect, 0.0 = totally fails).
            """;

    private final ChatClient chatClient;

    public LlmJudgeEvaluator(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /** 便捷方法：直接评估 {@link EvalCase}，criteria 取自 {@link EvalCase#judgeCriteria()}。 */
    public CaseResult.Check evaluateCase(EvalCase c, String actual) {
        if (c.judgeCriteria() == null || c.judgeCriteria().isBlank()) {
            return new CaseResult.Check(true, 1, "skipped (no judgeCriteria)");
        }
        EvaluationResponse resp = evaluate(new EvaluationRequest(c.judgeCriteria(), java.util.List.of(),
                buildJudgePayload(c.input(), actual)));
        return new CaseResult.Check(resp.isPass(), resp.getScore(), resp.getFeedback());
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        String userPrompt = """
                === Judging Criteria ===
                %s

                === Assistant's Answer (to be judged) ===
                %s
                """.formatted(safe(request.getUserText()), safe(request.getResponseContent()));
        try {
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            return parseJudgeResponse(raw);
        } catch (Exception e) {
            log.warn("LLM judge call failed: {}", e.getMessage());
            return new EvaluationResponse(false, 0f, "judge error: " + e.getMessage(), Map.of());
        }
    }

    // ---------- internals ----------

    static String buildJudgePayload(String input, String actual) {
        return "User input: " + safe(input) + "\n\n" + safe(actual);
    }

    static EvaluationResponse parseJudgeResponse(String raw) {
        if (raw == null) {
            return new EvaluationResponse(false, 0f, "empty judge response", Map.of());
        }
        String json = stripFences(raw).trim();
        try {
            Map<?, ?> m = MAPPER.readValue(json, Map.class);
            boolean pass = Boolean.TRUE.equals(m.get("pass"));
            double score = m.get("score") instanceof Number n ? n.doubleValue() : (pass ? 1.0 : 0.0);
            String reason = m.get("reason") == null ? "" : m.get("reason").toString();
            Map<String, Object> meta = new HashMap<>();
            meta.put("rawJudge", raw);
            return new EvaluationResponse(pass, (float) score, reason, meta);
        } catch (Exception e) {
            return new EvaluationResponse(false, 0f,
                    "judge response not parseable: " + truncate(raw, 120),
                    Map.of("rawJudge", raw));
        }
    }

    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "…");
    }
}
