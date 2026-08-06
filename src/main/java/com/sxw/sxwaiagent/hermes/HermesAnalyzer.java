package com.sxw.sxwaiagent.hermes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hermes 分析器
 * <p>
 * 监听 Agent 运行完成事件，使用 LLM 从对话中提取可复用的经验总结，
 * 生成候选记录供用户审核。LLM 异常时回退到启发式分析。
 */
@Component
public class HermesAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HermesAnalyzer.class);

    private static final String ANALYSIS_SYSTEM_PROMPT = """
        你是一个经验分析器。分析以下 Agent 对话，提取可复用的经验总结。
        
        输出要求：
        - 纯 JSON 数组，最多 3 条
        - 每条包含: type, title, content, confidence
        - type 取值: MEMORY | KNOWLEDGE | EVAL_CASE | AGENT_RULE
        - confidence: 0.0~1.0 的浮点数
        - 如果没有值得提取的内容，返回空数组 []
        - 不要输出任何 JSON 之外的文字
        
        示例：
        [{"type":"MEMORY","title":"用户偏好","content":"用户喜欢简洁回复","confidence":0.85}]
        """;

    private final HermesCandidateService candidateService;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;

    @Value("${sxw.hermes.llm-enabled:true}")
    private boolean llmEnabled;

    @Value("${sxw.hermes.min-confidence:0.6}")
    private double minConfidence;

    public HermesAnalyzer(HermesCandidateService candidateService,
                          ObjectMapper objectMapper,
                          ChatModel chatModel) {
        this.candidateService = candidateService;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    @Async
    @EventListener
    public void onAgentRunCompleted(AgentRunCompletedEvent event) {
        String requestId = event.getRequestId();
        log.debug("Analyzing completed agent run: {}", requestId);

        try {
            var response = event.getResponse();
            if (response == null) {
                log.debug("No response in event {}, skipping analysis", requestId);
                return;
            }

            String assistantReply = response.answer();
            String userMessage = event.getUserMessage();
            String chatId = event.getChatId();
            int toolCallCount = response.toolCalls() != null ? response.toolCalls().size() : 0;

            if (assistantReply == null || assistantReply.isBlank()) {
                return;
            }

            // 尝试 LLM 分析，失败则回退启发式
            boolean analyzed = false;
            if (llmEnabled) {
                analyzed = analyzeWithLLM(requestId, event.getTraceId(), chatId,
                        userMessage, assistantReply, toolCallCount);
            }
            if (!analyzed) {
                analyzeWithHeuristics(requestId, event.getTraceId(), chatId,
                        assistantReply, toolCallCount);
            }

        } catch (Exception e) {
            log.error("Failed to analyze agent run {}: {}", requestId, e.getMessage());
        }
    }

    /**
     * 使用 LLM 分析对话，提取候选
     *
     * @return true 表示 LLM 分析成功，false 表示需要回退启发式
     */
    private boolean analyzeWithLLM(String runId, String traceId, String chatId,
                                    String userMessage, String assistantReply, int toolCallCount) {
        try {
            String userContent = buildAnalysisInput(userMessage, assistantReply, toolCallCount);

            ChatResponse chatResponse = chatModel.call(new Prompt(List.of(
                    new SystemMessage(ANALYSIS_SYSTEM_PROMPT),
                    new UserMessage(userContent)
            )));

            String llmOutput = chatResponse.getResult().getOutput().getText();
            if (llmOutput == null || llmOutput.isBlank()) {
                log.warn("LLM returned empty analysis for run {}", runId);
                return false;
            }

            // 提取 JSON（可能被 markdown 包裹）
            String json = extractJson(llmOutput);
            List<Map<String, Object>> candidates = objectMapper.readValue(
                    json, new TypeReference<>() {});

            if (candidates.isEmpty()) {
                log.debug("LLM found no candidates for run {}", runId);
                return true;
            }

            int created = 0;
            for (Map<String, Object> item : candidates) {
                double confidence = item.get("confidence") instanceof Number n
                        ? n.doubleValue() : 0.0;
                if (confidence < minConfidence) {
                    log.debug("Skipping low-confidence candidate ({}) in run {}",
                            confidence, runId);
                    continue;
                }

                String type = String.valueOf(item.getOrDefault("type", "MEMORY"));
                String title = String.valueOf(item.getOrDefault("title", "未命名"));
                String content = String.valueOf(item.getOrDefault("content", ""));

                CandidateType candidateType = parseCandidateType(type);
                candidateService.createCandidate(
                        runId, chatId, candidateType, title, content,
                        objectMapper.writeValueAsString(Map.of(
                                "source", "llm_analysis",
                                "traceId", traceId,
                                "confidence", confidence
                        ))
                );
                created++;
            }

            log.info("LLM analysis for run {}: {} candidates created ({} total, threshold={})",
                    runId, created, candidates.size(), minConfidence);
            return true;

        } catch (Exception e) {
            log.warn("LLM analysis failed for run {}, falling back to heuristics: {}",
                    runId, e.getMessage());
            return false;
        }
    }

    /**
     * 启发式分析（回退方案）
     * <p>
     * 仅处理非 MEMORY 类型候选。MEMORY 候选必须来自 LLM 分析或用户显式指令，
     * 不能从 Assistant 回复关键词反推。
     */
    private void analyzeWithHeuristics(String runId, String traceId, String chatId,
                                        String assistantReply, int toolCallCount) {
        // 知识候选：长文本 + 技术内容
        if (assistantReply.length() > 500 && containsTechnicalContent(assistantReply)) {
            try {
                candidateService.createCandidate(
                        runId, chatId, CandidateType.KNOWLEDGE, "技术知识点",
                        extractKeyPoints(assistantReply),
                        objectMapper.writeValueAsString(Map.of("length", assistantReply.length()))
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for knowledge candidate in run {}", runId, e);
            }
        }

        // 评测用例候选：工具调用
        if (toolCallCount > 0) {
            try {
                candidateService.createCandidate(
                        runId, chatId, CandidateType.EVAL_CASE, "工具调用测试用例",
                        String.format("工具调用次数: %d\n回复长度: %d 字符",
                                toolCallCount, assistantReply.length()),
                        objectMapper.writeValueAsString(Map.of(
                                "tool_calls", toolCallCount,
                                "reply_length", assistantReply.length()))
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for eval candidate in run {}", runId, e);
            }
        }
    }

    // ───────────────────── Internal helpers ─────────────────────

    private String buildAnalysisInput(String userMessage, String assistantReply, int toolCallCount) {
        StringBuilder sb = new StringBuilder();
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append("用户消息: ").append(userMessage).append("\n\n");
        }
        sb.append("助手回复: ").append(assistantReply).append("\n\n");
        sb.append("工具调用次数: ").append(toolCallCount);
        return sb.toString();
    }

    private String extractJson(String text) {
        // 去除可能的 markdown 代码块包裹
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private CandidateType parseCandidateType(String type) {
        try {
            return CandidateType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CandidateType.MEMORY;
        }
    }

    private boolean containsTechnicalContent(String text) {
        return text.contains("步骤") || text.contains("方法") || text.contains("原理")
                || text.contains("```") || text.contains("代码");
    }

    private String extractKeyPoints(String text) {
        if (text.length() > 1000) {
            return text.substring(0, 1000) + "...";
        }
        return text;
    }
}
