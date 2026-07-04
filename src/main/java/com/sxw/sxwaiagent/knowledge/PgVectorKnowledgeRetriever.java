package com.sxw.sxwaiagent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * PgVector 知识检索器实现
 * 基于 Spring AI VectorStore 的知识检索
 */
@Slf4j
@Component
public class PgVectorKnowledgeRetriever implements KnowledgeRetriever {
    
    private final VectorStore vectorStore;
    
    @Autowired(required = false)
    public PgVectorKnowledgeRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    @Override
    public KnowledgeRetrievalResult retrieve(String query, int topK, double minScore) {
        if (!isAvailable() || query == null || query.isBlank()) {
            return KnowledgeRetrievalResult.empty(query);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(minScore)
                .build();
            
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            if (documents == null || documents.isEmpty()) {
                return KnowledgeRetrievalResult.empty(query);
            }
            
            List<KnowledgeChunk> chunks = documents.stream()
                .map(doc -> new KnowledgeChunk(
                    doc.getId(),
                    doc.getText(),
                    doc.getScore() != null ? doc.getScore() : 0.0,
                    "pgvector",
                    extractMetadata(doc, "document_id"),
                    extractMetadata(doc, "document_name")
                ))
                .toList();
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.debug("PgVector retrieved {} chunks for query '{}' in {}ms", 
                chunks.size(), query, elapsed);
            
            return new KnowledgeRetrievalResult(
                chunks,
                chunks.size(),
                query,
                elapsed
            );
            
        } catch (Exception e) {
            log.warn("PgVector retrieval failed: {}", e.getMessage());
            return KnowledgeRetrievalResult.empty(query);
        }
    }
    
    @Override
    public String getName() {
        return "pgvector";
    }
    
    @Override
    public boolean isAvailable() {
        return vectorStore != null;
    }
    
    private String extractMetadata(Document doc, String key) {
        if (doc.getMetadata() == null) {
            return "";
        }
        Object value = doc.getMetadata().get(key);
        return value != null ? value.toString() : "";
    }
}
