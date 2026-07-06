package com.sxw.sxwaiagent.hermes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
            
            // Note: userMessage and chatId are not in AgentResponse record
            // For now, skip analysis that requires userMessage
            if (assistantReply != null && !assistantReply.isBlank()) {
                analyzeForKnowledge(requestId, event.getTraceId(), assistantReply);
            }
            
            // Generate eval case if tools were called
            if (toolCallCount > 0) {
                analyzeForEvalCases(requestId, event.getTraceId(), assistantReply, toolCallCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze agent run {}: {}", requestId, e.getMessage());
        }
    }
    
    private void analyzeForMemories(String runId, String chatId, String userMessage) {
        // Extract user preferences and important facts
        // Simple heuristics - in production, use LLM to analyze
        if (userMessage.contains("记住") || userMessage.contains("偏好") || userMessage.contains("喜欢")) {
            candidateService.createCandidate(
                runId,
                chatId,
                CandidateType.MEMORY,
                "用户偏好记录",
                userMessage,
                "{\"source\": \"user_explicit\"}"
            );
        }
    }
    
    private void analyzeForKnowledge(String runId, String chatId, String assistantReply) {
        // Extract reusable knowledge from agent responses
        // Check if response contains structured knowledge
        if (assistantReply.length() > 500 && containsTechnicalContent(assistantReply)) {
            candidateService.createCandidate(
                runId,
                chatId,
                CandidateType.KNOWLEDGE,
                "技术知识点",
                extractKeyPoints(assistantReply),
                "{\"length\": " + assistantReply.length() + "}"
            );
        }
    }
    
    private void analyzeForEvalCases(String runId, String chatId, String assistantReply, int toolCallCount) {
        // Generate test cases from successful interactions with tools
        candidateService.createCandidate(
            runId,
            chatId,
            CandidateType.EVAL_CASE,
            "工具调用测试用例",
            String.format("工具调用次数: %d\n回复长度: %d 字符", toolCallCount, assistantReply.length()),
            "{\"tool_calls\": " + toolCallCount + ", \"reply_length\": " + assistantReply.length() + "}"
        );
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
