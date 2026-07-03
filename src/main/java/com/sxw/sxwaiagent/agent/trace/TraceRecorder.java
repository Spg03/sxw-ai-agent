package com.sxw.sxwaiagent.agent.trace;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 追踪记录器
 * <p>
 * 负责记录 Agent 执行过程中的各种追踪信息：
 * - 模型调用
 * - 工具调用
 * - 上下文变化
 * - 知识检索命中
 * <p>
 * P2 阶段使用 AgentTraceStore，后续升级到独立的 Trace 数据库。
 */
@Component
public class TraceRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);
    
    private final AgentTraceStore agentTraceStore;
    
    public TraceRecorder(AgentTraceStore agentTraceStore) {
        this.agentTraceStore = agentTraceStore;
    }
    
    /**
     * 记录工具调用
     */
    public void recordToolCall(
            String requestId,
            String traceId,
            int turn,
            String toolName,
            String arguments,
            String result,
            String status,
            long latencyMs
    ) {
        if (agentTraceStore == null || traceId == null) {
            log.warn("[{}] TraceRecorder: agentTraceStore or traceId is null, skipping tool call trace", requestId);
            return;
        }
        
        try {
            // 截断过长的参数和结果
            String inputSummary = truncate(arguments, 200);
            String outputSummary = truncate(result, 200);
            
            agentTraceStore.appendEvent(
                    traceId,
                    turn,
                    "tool_call",
                    toolName,
                    inputSummary,
                    outputSummary,
                    status,
                    latencyMs
            );
            
            log.debug("[{}] Recorded tool call: {} (turn {}, status={})", requestId, toolName, turn, status);
        } catch (Exception e) {
            log.error("[{}] Failed to record tool call trace: {}", requestId, e.getMessage(), e);
        }
    }
    
    /**
     * 记录模型调用
     */
    public void recordModelCall(
            String requestId,
            String traceId,
            int turn,
            String modelName,
            int inputTokens,
            int outputTokens,
            long latencyMs
    ) {
        if (agentTraceStore == null || traceId == null) {
            return;
        }
        
        try {
            String inputSummary = String.format("model=%s, input_tokens=%d, output_tokens=%d",
                    modelName, inputTokens, outputTokens);
            
            agentTraceStore.appendEvent(
                    traceId,
                    turn,
                    "model_call",
                    modelName,
                    inputSummary,
                    "",
                    "success",
                    latencyMs
            );
        } catch (Exception e) {
            log.error("[{}] Failed to record model call trace: {}", requestId, e.getMessage(), e);
        }
    }
    
    /**
     * 记录知识检索命中
     */
    public void recordKnowledgeHit(
            String requestId,
            String traceId,
            int turn,
            String knowledgeScope,
            int hitCount,
            long latencyMs
    ) {
        if (agentTraceStore == null || traceId == null) {
            return;
        }
        
        try {
            String inputSummary = String.format("scope=%s, hits=%d", knowledgeScope, hitCount);
            
            agentTraceStore.appendEvent(
                    traceId,
                    turn,
                    "knowledge_hit",
                    knowledgeScope,
                    inputSummary,
                    "",
                    "success",
                    latencyMs
            );
        } catch (Exception e) {
            log.error("[{}] Failed to record knowledge hit trace: {}", requestId, e.getMessage(), e);
        }
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
