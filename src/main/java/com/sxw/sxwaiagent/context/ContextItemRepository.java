package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Context item repository.
 * <p>
 * Persists context items to ai_context_item table for tracking and analysis.
 */
@Repository
public class ContextItemRepository {

    private static final Logger log = LoggerFactory.getLogger(ContextItemRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ContextItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Save context item.
     */
    public void save(ContextItem item) {
        String sql = """
                INSERT INTO ai_context_item 
                (request_id, trace_id, turn, section, content_type, content_preview, content_full, token_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql,
                item.requestId(),
                item.traceId(),
                item.turn(),
                item.section().name(),
                item.contentType(),
                item.contentPreview(),
                item.contentFull(),
                item.tokenCount(),
                item.createdAt()
        );

        log.debug("Saved context item: requestId={}, section={}, tokens={}",
                item.requestId(), item.section(), item.tokenCount());
    }

    /**
     * Batch save context items.
     */
    public void saveAll(List<ContextItem> items) {
        items.forEach(this::save);
    }

    /**
     * Query context items by requestId.
     */
    public List<ContextItem> findByRequestId(String requestId) {
        String sql = """
                SELECT id, request_id, trace_id, turn, section, content_type, 
                       content_preview, content_full, token_count, created_at
                FROM ai_context_item
                WHERE request_id = ?
                ORDER BY turn, created_at
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ContextItem(
                rs.getLong("id"),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getInt("turn"),
                ContextItem.Section.valueOf(rs.getString("section")),
                rs.getString("content_type"),
                rs.getString("content_preview"),
                rs.getString("content_full"),
                rs.getInt("token_count"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), requestId);
    }

    /**
     * Query context items by traceId.
     */
    public List<ContextItem> findByTraceId(String traceId) {
        String sql = """
                SELECT id, request_id, trace_id, turn, section, content_type, 
                       content_preview, content_full, token_count, created_at
                FROM ai_context_item
                WHERE trace_id = ?
                ORDER BY request_id, turn, created_at
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ContextItem(
                rs.getLong("id"),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getInt("turn"),
                ContextItem.Section.valueOf(rs.getString("section")),
                rs.getString("content_type"),
                rs.getString("content_preview"),
                rs.getString("content_full"),
                rs.getInt("token_count"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), traceId);
    }

    /**
     * Delete context items by requestId.
     */
    public void deleteByRequestId(String requestId) {
        String sql = "DELETE FROM ai_context_item WHERE request_id = ?";
        int deleted = jdbcTemplate.update(sql, requestId);
        log.debug("Deleted {} context items for requestId={}", deleted, requestId);
    }
}
