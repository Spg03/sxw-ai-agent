package com.sxw.sxwaiagent.knowledge;

import com.sxw.sxwaiagent.infrastructure.rag.RagFlowClient;
import com.sxw.sxwaiagent.infrastructure.rag.RagFlowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAGFlow 知识检索器实现
 * 基于 RAGFlow API 的知识检索
 */
@Slf4j
@Component
public class RagFlowKnowledgeRetriever implements KnowledgeRetriever {
    
    private final RagFlowClient ragFlowClient;
    private final RagFlowProperties properties;
    
    public RagFlowKnowledgeRetriever(RagFlowClient ragFlowClient, RagFlowProperties properties) {
        this.ragFlowClient = ragFlowClient;
        this.properties = properties;
    }
    
    @Override
    public KnowledgeRetrievalResult retrieve(String query, int topK, double minScore) {
        if (!isAvailable() || query == null || query.isBlank()) {
            return KnowledgeRetrievalResult.empty(query);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            RagFlowClient.RetrievalResult result = ragFlowClient.retrieve(query);
            
            if (result == null || result.chunks() == null || result.chunks().isEmpty()) {
                return KnowledgeRetrievalResult.empty(query);
            }
            
            List<KnowledgeChunk> chunks = result.chunks().stream()
                .filter(chunk -> chunk.similarity() >= minScore)
                .limit(topK)
                .map(chunk -> new KnowledgeChunk(
                    chunk.id(),
                    chunk.content(),
                    chunk.similarity(),
                    "ragflow",
                    chunk.documentId(),
                    findDocumentName(result, chunk.documentId())
                ))
                .toList();
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.debug("RAGFlow retrieved {} chunks for query '{}' in {}ms", 
                chunks.size(), query, elapsed);
            
            return new KnowledgeRetrievalResult(
                chunks,
                result.total(),
                query,
                elapsed
            );
            
        } catch (Exception e) {
            log.warn("RAGFlow retrieval failed: {}", e.getMessage());
            return KnowledgeRetrievalResult.empty(query);
        }
    }
    
    @Override
    public String getName() {
        return "ragflow";
    }
    
    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }
    
    private String findDocumentName(RagFlowClient.RetrievalResult result, String documentId) {
        if (result.docAggs() == null || documentId == null) {
            return "";
        }
        
        return result.docAggs().stream()
            .filter(agg -> documentId.equals(agg.docId()))
            .map(RagFlowClient.DocAgg::docName)
            .filter(name -> name != null && !name.isBlank())
            .findFirst()
            .orElse("");
    }
}
