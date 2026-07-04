package com.sxw.sxwaiagent.knowledge;

import java.util.List;

/**
 * 知识检索结果
 * 统一的知识块表示，支持来自 RAGFlow 或 PgVector 等不同来源
 */
public record KnowledgeChunk(
    String chunkId,
    String content,
    double similarity,
    String source,
    String documentId,
    String documentName
) {
    public KnowledgeChunk {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId cannot be null or blank");
        }
        if (content == null) {
            content = "";
        }
        if (source == null) {
            source = "unknown";
        }
    }
}
