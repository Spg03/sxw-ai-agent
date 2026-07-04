package com.sxw.sxwaiagent.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * 知识检索结果
 * 封装检索到的知识块列表和元数据
 */
public record KnowledgeRetrievalResult(
    List<KnowledgeChunk> chunks,
    int totalHits,
    String query,
    long retrievalTimeMs
) {
    public KnowledgeRetrievalResult {
        if (chunks == null) {
            chunks = Collections.emptyList();
        }
        if (query == null) {
            query = "";
        }
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public int size() {
        return chunks.size();
    }

    public static KnowledgeRetrievalResult empty(String query) {
        return new KnowledgeRetrievalResult(Collections.emptyList(), 0, query, 0L);
    }
}
