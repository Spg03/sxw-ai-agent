package com.sxw.sxwaiagent.infrastructure.eval;

import java.util.List;

/**
 * 一条评测用例。来自 {@code classpath:eval/*.yaml}，YAML 字段与本 record 一致：
 * <pre>
 * - id: love-001
 *   category: empathy
 *   input: 我和女朋友吵架了，怎么办
 *   expectKeywordsAny: ["共情", "理解", "倾听"]
 *   judgeCriteria: 回答是否表现出共情、不直接给生硬建议、用中文
 *   maxLatencyMs: 30000
 * </pre>
 *
 * @param id                用例唯一 id
 * @param category          类别（统计分组用，如 empathy / rag-recall / safety）
 * @param input             发给被测系统的用户消息
 * @param expectKeywordsAny 命中其中任一关键词即视为关键词检查通过；空 = 跳过此项
 * @param expectKeywordsAll 所有关键词都必须命中；空 = 跳过此项
 * @param judgeCriteria     LLM-as-Judge 的判断准则；空 = 跳过 LLM 评估
 * @param maxLatencyMs      超过该延迟视为延迟不达标；&lt;=0 = 不检查
 */
public record EvalCase(
        String id,
        String category,
        String input,
        List<String> expectKeywordsAny,
        List<String> expectKeywordsAll,
        String judgeCriteria,
        long maxLatencyMs
) {}
