package com.sxw.sxwaiagent.knowledge;

/**
 * 知识检索器接口
 * 统一的知识检索抽象，支持多种后端实现（RAGFlow、PgVector 等）
 */
public interface KnowledgeRetriever {
    
    /**
     * 检索相关知识
     * @param query 查询文本
     * @param topK 返回的最大结果数
     * @param minScore 最小相似度阈值
     * @return 检索结果
     */
    KnowledgeRetrievalResult retrieve(String query, int topK, double minScore);
    
    /**
     * 检索相关知识（使用默认参数）
     * @param query 查询文本
     * @return 检索结果
     */
    default KnowledgeRetrievalResult retrieve(String query) {
        return retrieve(query, 5, 0.5);
    }
    
    /**
     * 获取检索器名称
     * @return 检索器标识
     */
    String getName();
    
    /**
     * 检查检索器是否可用
     * @return 是否已配置并可用
     */
    boolean isAvailable();
}
