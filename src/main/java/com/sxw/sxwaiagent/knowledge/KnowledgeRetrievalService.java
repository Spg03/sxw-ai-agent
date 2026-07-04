package com.sxw.sxwaiagent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 知识检索服务
 * 统一的检索入口，支持多后端策略
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {
    
    private final List<KnowledgeRetriever> retrievers;
    
    public KnowledgeRetrievalService(List<KnowledgeRetriever> retrievers) {
        this.retrievers = retrievers;
        log.info("Initialized KnowledgeRetrievalService with {} retrievers: {}", 
            retrievers.size(),
            retrievers.stream().map(KnowledgeRetriever::getName).toList());
    }
    
    /**
     * 使用所有可用的检索器检索知识
     * @param query 查询文本
     * @param topK 每个检索器返回的最大结果数
     * @param minScore 最小相似度阈值
     * @return 合并后的检索结果
     */
    public KnowledgeRetrievalResult retrieveFromAll(String query, int topK, double minScore) {
        List<KnowledgeChunk> allChunks = retrievers.stream()
            .filter(KnowledgeRetriever::isAvailable)
            .flatMap(retriever -> retriever.retrieve(query, topK, minScore).chunks().stream())
            .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
            .limit(topK)
            .toList();
        
        return new KnowledgeRetrievalResult(
            allChunks,
            allChunks.size(),
            query,
            0L
        );
    }
    
    /**
     * 使用指定检索器检索知识
     * @param retrieverName 检索器名称
     * @param query 查询文本
     * @param topK 返回的最大结果数
     * @param minScore 最小相似度阈值
     * @return 检索结果
     */
    public KnowledgeRetrievalResult retrieveFrom(String retrieverName, String query, int topK, double minScore) {
        return findRetriever(retrieverName)
            .map(retriever -> retriever.retrieve(query, topK, minScore))
            .orElseGet(() -> {
                log.warn("Retriever '{}' not found or not available", retrieverName);
                return KnowledgeRetrievalResult.empty(query);
            });
    }
    
    /**
     * 使用所有可用的检索器检索知识（默认参数）
     * @param query 查询文本
     * @return 检索结果
     */
    public KnowledgeRetrievalResult retrieve(String query) {
        return retrieveFromAll(query, 5, 0.5);
    }
    
    /**
     * 获取所有可用的检索器
     * @return 可用的检索器列表
     */
    public List<KnowledgeRetriever> getAvailableRetrievers() {
        return retrievers.stream()
            .filter(KnowledgeRetriever::isAvailable)
            .toList();
    }
    
    /**
     * 检查是否有任何检索器可用
     * @return 是否有可用检索器
     */
    public boolean hasAvailableRetriever() {
        return retrievers.stream().anyMatch(KnowledgeRetriever::isAvailable);
    }
    
    private Optional<KnowledgeRetriever> findRetriever(String name) {
        return retrievers.stream()
            .filter(r -> r.getName().equals(name))
            .filter(KnowledgeRetriever::isAvailable)
            .findFirst();
    }
}
