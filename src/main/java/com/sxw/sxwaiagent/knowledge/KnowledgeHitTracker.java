package com.sxw.sxwaiagent.knowledge;

import com.sxw.sxwaiagent.agent.trace.TraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识命中追踪器
 * 记录知识检索的命中情况，用于分析和优化
 */
@Slf4j
@Component
public class KnowledgeHitTracker {
    
    private final TraceRecorder traceRecorder;
    
    public KnowledgeHitTracker(TraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }
    
    /**
     * 记录知识检索命中
     * @param requestId 请求 ID
     * @param traceId 追踪 ID
     * @param turn 对话轮次
     * @param result 检索结果
     * @param knowledgeScope 知识范围标识
     */
    public void track(
        String requestId, 
        String traceId, 
        int turn, 
        KnowledgeRetrievalResult result,
        String knowledgeScope
    ) {
        if (result == null || result.isEmpty()) {
            log.debug("No knowledge hits to track for requestId={}", requestId);
            return;
        }
        
        try {
            traceRecorder.recordKnowledgeHit(
                requestId,
                traceId,
                turn,
                knowledgeScope,
                result.size(),
                result.retrievalTimeMs()
            );
            
            log.debug("Tracked {} knowledge hits for requestId={}, turn={}", 
                result.size(), requestId, turn);
                
        } catch (Exception e) {
            log.warn("Failed to track knowledge hits: {}", e.getMessage());
        }
    }
    
    private String buildInputSummary(KnowledgeRetrievalResult result, String scope) {
        return String.format("scope=%s, query='%s', hits=%d, time=%dms",
            scope,
            result.query(),
            result.size(),
            result.retrievalTimeMs());
    }
    
    private String buildOutputSummary(KnowledgeRetrievalResult result) {
        if (result.isEmpty()) {
            return "No chunks retrieved";
        }
        
        KnowledgeChunk topChunk = result.chunks().get(0);
        return String.format("Top chunk: id=%s, score=%.3f, source=%s, doc=%s",
            topChunk.chunkId(),
            topChunk.similarity(),
            topChunk.source(),
            topChunk.documentName());
    }
}
