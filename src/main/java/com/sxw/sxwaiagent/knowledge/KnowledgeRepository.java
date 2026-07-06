package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识库持久化操作
 * 
 * 负责文档和文档块的数据库 CRUD 操作。
 */
@Repository
public class KnowledgeRepository {
    
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 保存文档元数据
     */
    public String saveDocument(String title, String sourcePath, int chunkCount) {
        String docId = UUID.randomUUID().toString();
        
        jdbcTemplate.update("""
            INSERT INTO ai_knowledge_document (doc_id, title, source_path, chunk_count, status, created_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?)
            """,
            docId, title, sourcePath, chunkCount, Timestamp.from(Instant.now())
        );
        
        log.info("Saved document: {} ({} chunks)", docId, chunkCount);
        return docId;
    }
    
    /**
     * 批量保存文档块
     */
    public void saveChunks(List<MarkdownTextSplitter.DocumentChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Chunks and embeddings size mismatch");
        }
        
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownTextSplitter.DocumentChunk chunk = chunks.get(i);
            float[] embedding = embeddings.get(i);
            
            // 将 float[] 转换为 PostgreSQL vector 格式
            String vectorStr = "[" + arrayToString(embedding) + "]";
            
            jdbcTemplate.update("""
                INSERT INTO ai_knowledge_chunk (chunk_id, doc_id, chunk_index, breadcrumb, content, token_count, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?)
                """,
                chunk.chunkId(),
                chunk.docId(),
                chunk.chunkIndex(),
                chunk.breadcrumb(),
                chunk.content(),
                chunk.tokenCount(),
                vectorStr,
                Timestamp.from(Instant.now())
            );
        }
        
        log.debug("Saved {} chunks for document {}", chunks.size(), chunks.isEmpty() ? "N/A" : chunks.get(0).docId());
    }
    
    /**
     * 查询相似文档块（向量相似度搜索）
     */
    public List<KnowledgeChunkRecord> findSimilarChunks(float[] queryEmbedding, int topK, double minScore) {
        String vectorStr = "[" + arrayToString(queryEmbedding) + "]";
        
        return jdbcTemplate.query("""
            SELECT 
                chunk_id, doc_id, chunk_index, breadcrumb, content, token_count,
                1 - (embedding <=> ?::vector) as similarity
            FROM ai_knowledge_chunk
            WHERE 1 - (embedding <=> ?::vector) >= ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """,
            (rs, rowNum) -> new KnowledgeChunkRecord(
                rs.getString("chunk_id"),
                rs.getString("doc_id"),
                rs.getInt("chunk_index"),
                rs.getString("breadcrumb"),
                rs.getString("content"),
                rs.getInt("token_count"),
                rs.getDouble("similarity")
            ),
            vectorStr, vectorStr, minScore, vectorStr, topK
        );
    }
    
    /**
     * 删除文档及其所有块
     */
    public void deleteDocument(String docId) {
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = ?", docId);
        jdbcTemplate.update("DELETE FROM ai_knowledge_document WHERE doc_id = ?", docId);
        log.info("Deleted document: {}", docId);
    }
    
    /**
     * 列出所有文档
     */
    public List<KnowledgeDocumentRecord> listDocuments() {
        return jdbcTemplate.query("""
            SELECT doc_id, title, source_path, chunk_count, status, created_at
            FROM ai_knowledge_document
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new KnowledgeDocumentRecord(
                rs.getString("doc_id"),
                rs.getString("title"),
                rs.getString("source_path"),
                rs.getInt("chunk_count"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
            )
        );
    }
    
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        return sb.toString();
    }
    
    public record KnowledgeChunkRecord(
        String chunkId,
        String docId,
        int chunkIndex,
        String breadcrumb,
        String content,
        int tokenCount,
        double similarity
    ) {}
    
    public record KnowledgeDocumentRecord(
        String docId,
        String title,
        String sourcePath,
        int chunkCount,
        String status,
        Instant createdAt
    ) {}
}
