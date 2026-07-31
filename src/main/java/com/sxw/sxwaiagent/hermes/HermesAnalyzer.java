package com.sxw.sxwaiagent.hermes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hermes 分析器
 * 
 * 监听 Agent 运行完成事件，从对话中提取可复用的经验总结，
 * 生成候选记录供用户审核。
 */
@Component
public class HermesAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(HermesAnalyzer.class);
    
    private final HermesCandidateService candidateService;
    private final ObjectMapper objectMapper;
    
    public HermesAnalyzer(HermesCandidateService candidateService, ObjectMapper objectMapper) {
        this.candidateService = candidateService;
        this.objectMapper = objectMapper;
    }
    
    @EventListener
    public void onAgentRunCompleted(AgentRunCompletedEvent event) {
        String requestId = event.getRequestId();
        log.debug("Analyzing completed agent run: {}", requestId);
        
        try {
            // Extract data from response (AgentResponse is a record)
            var response = event.getResponse();
            if (response == null) {
                log.debug("No response in event {}, skipping analysis", requestId);
                return;
            }
            
            // AgentResponse record: requestId, traceId, answer, citations, toolCalls, latencyMs
            String assistantReply = response.answer();
            int toolCallCount = response.toolCalls() != null ? response.toolCalls().size() : 0;
            
            // Analyze for memory candidates from assistant reply
            if (assistantReply != null && !assistantReply.isBlank()) {
                analyzeForMemories(requestId, event.getTraceId(), assistantReply);
                analyzeForKnowledge(requestId, event.getTraceId(), assistantReply);
            }
            
            // Generate eval case if tools were called
            if (toolCallCount > 0 && assistantReply != null) {
                analyzeForEvalCases(requestId, event.getTraceId(), assistantReply, toolCallCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze agent run {}: {}", requestId, e.getMessage());
        }
    }
    
    /**
     * 从 assistant 回复中提取用户偏好和重要事实
     * <p>
     * 由于事件不携带 userMessage，改为分析 assistant 回复中的记忆关键词。
     * 在生产环境中应使用 LLM 进行更精准的分析。
     */
    private void analyzeForMemories(String runId, String traceId, String assistantReply) {
        // Simple heuristics - in production, use LLM to analyze
        if (assistantReply.contains("记住") || assistantReply.contains("偏好")
                || assistantReply.contains("喜欢") || assistantReply.contains("已记录")) {
            try {
                candidateService.createCandidate(
                    runId,
                    null, // TODO: AgentRunCompletedEvent 不含 chatId，后续应在事件中添加
                    CandidateType.MEMORY,
                    "用户偏好记录",
                    assistantReply.length() > 500 ? assistantReply.substring(0, 500) + "..." : assistantReply,
                    objectMapper.writeValueAsString(Map.of("source", "assistant_reply", "traceId", traceId))
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for memory candidate in run {}", runId, e);
            }
        }
    }

    private void analyzeForKnowledge(String runId, String traceId, String assistantReply) {
        // Extract reusable knowledge from agent responses
        // Check if response contains structured knowledge
        if (assistantReply.length() > 500 && containsTechnicalContent(assistantReply)) {
            try {
                candidateService.createCandidate(
                    runId,
                    null, // TODO: AgentRunCompletedEvent 不含 chatId，后续应在事件中添加
                    CandidateType.KNOWLEDGE,
                    "技术知识点",
                    extractKeyPoints(assistantReply),
                    objectMapper.writeValueAsString(Map.of("length", assistantReply.length()))
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for knowledge candidate in run {}", runId, e);
            }
        }
    }

    private void analyzeForEvalCases(String runId, String traceId, String assistantReply, int toolCallCount) {
        // Generate test cases from successful interactions with tools
        try {
            candidateService.createCandidate(
                runId,
                null, // TODO: AgentRunCompletedEvent 不含 chatId，后续应在事件中添加
                CandidateType.EVAL_CASE,
                "工具调用测试用例",
                String.format("工具调用次数: %d\n回复长度: %d 字符", toolCallCount, assistantReply.length()),
                objectMapper.writeValueAsString(Map.of("tool_calls", toolCallCount, "reply_length", assistantReply.length()))
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata for eval candidate in run {}", runId, e);
        }
    }
    
    private boolean containsTechnicalContent(String text) {
        return text.contains("步骤") || text.contains("方法") || text.contains("原理") ||
               text.contains("```") || text.contains("代码");
    }
    
    private String extractKeyPoints(String text) {
        // Simple extraction - in production, use LLM
        if (text.length() > 1000) {
            return text.substring(0, 1000) + "...";
        }
        return text;
    }
}
