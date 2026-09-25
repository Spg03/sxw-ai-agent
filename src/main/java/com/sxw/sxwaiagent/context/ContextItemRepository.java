package com.sxw.sxwaiagent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
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
                (request_id,trace_id,conversation_id,user_id,call_no,section_type,section_key,section_kind,
                 item_type,item_id,inclusion_status,content_hash,content_length,source_ref,
                 content_preview,token_count,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        jdbcTemplate.update(sql,
                item.requestId(),
                item.traceId(),
                item.conversationId(),
                item.userId(),
                item.callNo(),
                item.sectionKind(),
                item.sectionKey(),
                item.sectionKind(),
                item.itemType(),
                item.itemId(),
                item.inclusionStatus(),
                item.contentHash(),
                item.contentLength(),
                item.sourceRef(),
                item.contentPreview(),
                item.tokenCount(),
                item.createdAt()
        );

        log.debug("Saved context item: requestId={}, section={}, tokens={}",
                item.requestId(), item.sectionKey(), item.tokenCount());
    }

    /**
     * Batch save context items.
     */
    public void saveAll(List<ContextItem> items) {
        if (items == null || items.isEmpty()) return;
        String sql = """
                INSERT INTO ai_context_item
                (request_id,trace_id,conversation_id,user_id,call_no,section_type,section_key,section_kind,
                 item_type,item_id,inclusion_status,content_hash,content_length,source_ref,
                 content_preview,token_count,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int index) throws java.sql.SQLException {
                ContextItem item = items.get(index);
                ps.setString(1, item.requestId());
                ps.setString(2, item.traceId());
                ps.setString(3, item.conversationId());
                if (item.userId() == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, item.userId());
                ps.setInt(5, item.callNo());
                ps.setString(6, item.sectionKind());
                ps.setString(7, item.sectionKey());
                ps.setString(8, item.sectionKind());
                ps.setString(9, item.itemType());
                ps.setString(10, item.itemId());
                ps.setString(11, item.inclusionStatus());
                ps.setString(12, item.contentHash());
                ps.setInt(13, item.contentLength());
                ps.setString(14, item.sourceRef());
                ps.setString(15, item.contentPreview());
                ps.setInt(16, item.tokenCount());
                ps.setObject(17, item.createdAt());
            }

            @Override
            public int getBatchSize() { return items.size(); }
        });
        log.debug("Saved {} context audit items for requestId={}", items.size(), items.getFirst().requestId());
    }

    /**
     * Query context items by requestId.
     */
    public List<ContextItem> findByRequestId(String requestId) {
        String sql = """
                SELECT id,request_id,trace_id,conversation_id,user_id,call_no,section_key,section_kind,
                       item_type,item_id,inclusion_status,content_hash,content_length,source_ref,
                       content_preview,token_count,created_at
                FROM ai_context_item
                WHERE request_id = ?
                ORDER BY call_no, id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> map(rs), requestId);
    }

    /**
     * Query context items by traceId.
     */
    public List<ContextItem> findByTraceId(String traceId) {
        String sql = """
                SELECT id,request_id,trace_id,conversation_id,user_id,call_no,section_key,section_kind,
                       item_type,item_id,inclusion_status,content_hash,content_length,source_ref,
                       content_preview,token_count,created_at
                FROM ai_context_item
                WHERE trace_id = ?
                ORDER BY request_id, call_no, id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> map(rs), traceId);
    }

    /**
     * Delete context items by requestId.
     */
    public void deleteByRequestId(String requestId) {
        String sql = "DELETE FROM ai_context_item WHERE request_id = ?";
        int deleted = jdbcTemplate.update(sql, requestId);
        log.debug("Deleted {} context items for requestId={}", deleted, requestId);
    }

    private ContextItem map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long owner = rs.getLong("user_id");
        Long userId = rs.wasNull() ? null : owner;
        return new ContextItem(rs.getLong("id"), rs.getString("request_id"), rs.getString("trace_id"),
                rs.getString("conversation_id"), userId, rs.getInt("call_no"),
                rs.getString("section_key"), rs.getString("section_kind"), rs.getString("item_type"),
                rs.getString("item_id"), rs.getString("inclusion_status"), rs.getString("content_hash"),
                rs.getInt("content_length"), rs.getString("source_ref"), rs.getString("content_preview"),
                rs.getInt("token_count"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
