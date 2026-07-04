package com.sxw.sxwaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 记忆仓储
 * <p>
 * 负责 MemoryItem 的持久化操作。
 */
@Repository
public class MemoryRepository {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public MemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 保存记忆项
     */
    public void save(MemoryItem item) {
        String sql = """
            INSERT INTO ai_memory_item (
                memory_id, memory_type, name, description,
                rule_text, why_text, apply_text,
                source_trace_id, confidence, status,
                created_at, expired_at, reviewed_at, reviewed_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (memory_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                rule_text = EXCLUDED.rule_text,
                why_text = EXCLUDED.why_text,
                apply_text = EXCLUDED.apply_text,
                confidence = EXCLUDED.confidence,
                status = EXCLUDED.status,
                reviewed_at = EXCLUDED.reviewed_at,
                reviewed_by = EXCLUDED.reviewed_by
            """;
        
        jdbcTemplate.update(sql,
            item.memoryId(),
            item.memoryType().name(),
            item.name(),
            item.description(),
            item.ruleText(),
            item.whyText(),
            item.applyText(),
            item.sourceTraceId(),
            item.confidence(),
            item.status().name(),
            item.createdAt(),
            item.expiredAt(),
            item.reviewedAt(),
            item.reviewedBy()
        );
        
        log.debug("Saved memory item: {}", item.memoryId());
    }
    
    /**
     * 根据 memoryId 查找
     */
    public Optional<MemoryItem> findByMemoryId(String memoryId) {
        String sql = "SELECT * FROM ai_memory_item WHERE memory_id = ?";
        List<MemoryItem> items = jdbcTemplate.query(sql, new MemoryItemRowMapper(), memoryId);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }
    
    /**
     * 查找所有活跃记忆（用于 Index）
     */
    public List<MemoryItem> findAllActive() {
        String sql = """
            SELECT * FROM ai_memory_item 
            WHERE status = 'ACTIVE' 
              AND (expired_at IS NULL OR expired_at > CURRENT_TIMESTAMP)
            ORDER BY confidence DESC, created_at DESC
            LIMIT 20
            """;
        return jdbcTemplate.query(sql, new MemoryItemRowMapper());
    }
    
    /**
     * 根据类型查找活跃记忆
     */
    public List<MemoryItem> findByTypeAndStatus(MemoryType type, MemoryStatus status) {
        String sql = """
            SELECT * FROM ai_memory_item 
            WHERE memory_type = ? AND status = ?
              AND (expired_at IS NULL OR expired_at > CURRENT_TIMESTAMP)
            ORDER BY confidence DESC
            """;
        return jdbcTemplate.query(sql, new MemoryItemRowMapper(), type.name(), status.name());
    }
    
    /**
     * 更新记忆状态
     */
    public void updateStatus(String memoryId, MemoryStatus status, String reviewedBy) {
        String sql = """
            UPDATE ai_memory_item 
            SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?
            WHERE memory_id = ?
            """;
        jdbcTemplate.update(sql, status.name(), reviewedBy, memoryId);
        log.debug("Updated memory status: {} -> {}", memoryId, status);
    }
    
    /**
     * 删除记忆项
     */
    public void delete(String memoryId) {
        String sql = "DELETE FROM ai_memory_item WHERE memory_id = ?";
        jdbcTemplate.update(sql, memoryId);
        log.debug("Deleted memory item: {}", memoryId);
    }
    
    /**
     * 统计记忆数量
     */
    public long countActive() {
        String sql = """
            SELECT COUNT(*) FROM ai_memory_item 
            WHERE status = 'ACTIVE' 
              AND (expired_at IS NULL OR expired_at > CURRENT_TIMESTAMP)
            """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    private static class MemoryItemRowMapper implements RowMapper<MemoryItem> {
        @Override
        public MemoryItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MemoryItem(
                rs.getLong("id"),
                rs.getString("memory_id"),
                MemoryType.valueOf(rs.getString("memory_type")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("rule_text"),
                rs.getString("why_text"),
                rs.getString("apply_text"),
                rs.getString("source_trace_id"),
                rs.getBigDecimal("confidence"),
                MemoryStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("expired_at") != null ? rs.getTimestamp("expired_at").toLocalDateTime() : null,
                rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toLocalDateTime() : null,
                rs.getString("reviewed_by")
            );
        }
    }
}
