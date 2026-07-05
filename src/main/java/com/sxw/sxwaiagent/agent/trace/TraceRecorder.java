package com.sxw.sxwaiagent.agent.trace;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Trace recorder.
 * <p>
 * Records various trace information during Agent execution:
 * - Model calls
 * - Tool calls
 * - Context changes
 * - Knowledge retrieval hits
 * <p>
 * P2 phase uses AgentTraceStore, later upgrade to dedicated Trace database.
 */
@Component
public class TraceRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);
    
    private final AgentTraceStore agentTraceStore;
    
    public TraceRecorder(AgentTraceStore agentTraceStore) {
        this.agentTraceStore = agentTraceStore;
    }
    
    /**
     * Record tool call.
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
            // Truncate long arguments and results
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
        } catch (RuntimeException e) {
            log.error("[{}] Failed to record tool call trace", requestId, e);
        }
    }
    
    /**
     * Record model call.
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
        } catch (RuntimeException e) {
            log.error("[{}] Failed to record model call trace", requestId, e);
        }
    }
    
    /**
     * Record knowledge retrieval hit.
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
        } catch (RuntimeException e) {
            log.error("[{}] Failed to record knowledge hit trace", requestId, e);
        }
    }
    
    /**
     * Truncate string.
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
