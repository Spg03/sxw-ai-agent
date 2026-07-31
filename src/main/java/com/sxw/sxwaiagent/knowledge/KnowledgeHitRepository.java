package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 知识命中持久化仓库
 * 负责将知识检索命中记录批量写入 ai_knowledge_hit 表，并提供聚合查询。
 */
@Repository
public class KnowledgeHitRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHitRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeHitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量写入命中记录
     *
     * @param requestId 请求 ID
     * @param hits      命中列表（chunk_id, doc_id, score, rank）
     */
    public void saveHits(String requestId, List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }

        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> batchArgs = hits.stream()
            .map(h -> new Object[]{requestId, h.chunkId(), h.docId(), h.score(), h.rank(), now})
            .toList();

        jdbcTemplate.batchUpdate("""
            INSERT INTO ai_knowledge_hit (request_id, chunk_id, doc_id, score, rank, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            batchArgs
        );

        log.debug("Saved {} knowledge hits for requestId={}", hits.size(), requestId);
    }

    /**
     * 查询最热门 chunks（按命中次数降序）
     *
     * @param limit 返回条数
     * @return chunk_id + hit_count 列表
     */
    public List<TopChunkRecord> findTopChunks(int limit) {
        return jdbcTemplate.query("""
            SELECT chunk_id, COUNT(*) AS hit_count
            FROM ai_knowledge_hit
            GROUP BY chunk_id
            ORDER BY hit_count DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new TopChunkRecord(
                rs.getString("chunk_id"),
                rs.getLong("hit_count")
            ),
            limit
        );
    }

    /**
     * 统计文档命中次数
     *
     * @param docId 文档 ID
     * @return 命中总数
     */
    public long countByDocId(String docId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ai_knowledge_hit WHERE doc_id = ?",
            Long.class,
            docId
        );
        return count != null ? count : 0L;
    }

    /** 命中记录（对应 ai_knowledge_hit 行） */
    public record KnowledgeHit(
        String chunkId,
        String docId,
        double score,
        int rank
    ) {}

    /** 热门 chunk 聚合结果 */
    public record TopChunkRecord(
        String chunkId,
        long hitCount
    ) {}
}
