package com.sxw.sxwaiagent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated Uses legacy ai_trace table. Use {@link com.sxw.sxwaiagent.infrastructure.trace.DbAgentTraceRepository} instead.
 */
@Deprecated
@Repository
public class TraceRepository {
    
    private static final Logger log = LoggerFactory.getLogger(TraceRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public TraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public void save(TraceRecord record) {
        jdbcTemplate.update("""
            INSERT INTO ai_trace (trace_id, agent_type, input, output, tool_calls, latency_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            record.traceId(),
            record.agentType(),
            record.input(),
            record.output(),
            record.toolCalls(),
            record.latencyMs(),
            Timestamp.from(record.createdAt())
        );
        
        log.debug("Saved trace: {}", record.traceId());
    }
    
    public Optional<TraceRecord> findByTraceId(String traceId) {
        List<TraceRecord> results = jdbcTemplate.query("""
            SELECT * FROM ai_trace WHERE trace_id = ?
            """,
            (rs, rowNum) -> new TraceRecord(
                rs.getString("trace_id"),
                rs.getString("agent_type"),
                rs.getString("input"),
                rs.getString("output"),
                rs.getString("tool_calls"),
                rs.getLong("latency_ms"),
                rs.getTimestamp("created_at").toInstant()
            ),
            traceId
        );
        
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    public List<TraceRecord> findByAgentType(String agentType, int limit) {
        return jdbcTemplate.query("""
            SELECT * FROM ai_trace 
            WHERE agent_type = ?
            ORDER BY created_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new TraceRecord(
                rs.getString("trace_id"),
                rs.getString("agent_type"),
                rs.getString("input"),
                rs.getString("output"),
                rs.getString("tool_calls"),
                rs.getLong("latency_ms"),
                rs.getTimestamp("created_at").toInstant()
            ),
            agentType,
            limit
        );
    }
    
    public List<TraceRecord> findRecent(int limit) {
        return jdbcTemplate.query("""
            SELECT * FROM ai_trace 
            ORDER BY created_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new TraceRecord(
                rs.getString("trace_id"),
                rs.getString("agent_type"),
                rs.getString("input"),
                rs.getString("output"),
                rs.getString("tool_calls"),
                rs.getLong("latency_ms"),
                rs.getTimestamp("created_at").toInstant()
            ),
            limit
        );
    }
}
