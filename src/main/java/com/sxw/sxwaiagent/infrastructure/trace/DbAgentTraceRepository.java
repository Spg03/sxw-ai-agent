package com.sxw.sxwaiagent.infrastructure.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class DbAgentTraceRepository {

    private static final Logger log = LoggerFactory.getLogger(DbAgentTraceRepository.class);
    private static final TypeReference<List<AgentTraceEvent>> EVENT_LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DbAgentTraceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveRun(String traceId, String chatId, Instant startedAt, String status) {
        jdbcTemplate.update("""
            INSERT INTO ai_request_trace (request_id, trace_id, chat_id, status, started_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (request_id) DO UPDATE
            SET status = EXCLUDED.status,
                trace_id = EXCLUDED.trace_id,
                chat_id = EXCLUDED.chat_id,
                started_at = EXCLUDED.started_at
            """,
                traceId, traceId, chatId, status, Timestamp.from(startedAt));
    }

    public void updateEvents(String traceId, List<AgentTraceEvent> events) {
        try {
            String json = objectMapper.writeValueAsString(events);
            jdbcTemplate.update(
                    "UPDATE ai_request_trace SET events_json = ? WHERE request_id = ?",
                    json, traceId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize events for trace {}", traceId, e);
        }
    }

    public void updateStatus(String traceId, String status, Instant finishedAt) {
        jdbcTemplate.update(
                "UPDATE ai_request_trace SET status = ?, finished_at = ? WHERE request_id = ?",
                status, Timestamp.from(finishedAt), traceId);
    }

    public List<AgentTraceRun> findRecentByChatId(String chatId, int limit) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, chat_id, status, started_at, finished_at, events_json
            FROM ai_request_trace
            WHERE chat_id = ?
            ORDER BY started_at DESC
            LIMIT ?
            """,
                (rs, rowNum) -> {
                    String traceIdVal = rs.getString("request_id");
                    String chatIdVal = rs.getString("chat_id");
                    Instant startedAtVal = rs.getTimestamp("started_at").toInstant();
                    Timestamp finishedTs = rs.getTimestamp("finished_at");
                    Instant finishedAtVal = finishedTs != null ? finishedTs.toInstant() : null;
                    String statusVal = rs.getString("status");
                    String eventsJson = rs.getString("events_json");
                    List<AgentTraceEvent> events = deserializeEvents(eventsJson);
                    return new AgentTraceRun(traceIdVal, chatIdVal, startedAtVal, finishedAtVal, statusVal, events);
                },
                chatId, limit);
    }

    public Set<String> findExistingTraceIds(String chatId) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT request_id FROM ai_request_trace WHERE chat_id = ?
            """, String.class, chatId);
        return Set.copyOf(ids);
    }

    public List<String> findAllTraceIdsByChatId(String chatId) {
        return jdbcTemplate.queryForList("""
            SELECT request_id FROM ai_request_trace
            WHERE chat_id = ?
            ORDER BY started_at ASC
            """, String.class, chatId);
    }

    public List<AgentTraceRun> findRecentByTraceId(String traceId) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, chat_id, status, started_at, finished_at, events_json
            FROM ai_request_trace
            WHERE request_id = ?
            LIMIT 1
            """,
                (rs, rowNum) -> {
                    String traceIdVal = rs.getString("request_id");
                    String chatIdVal = rs.getString("chat_id");
                    Instant startedAtVal = rs.getTimestamp("started_at").toInstant();
                    Timestamp finishedTs = rs.getTimestamp("finished_at");
                    Instant finishedAtVal = finishedTs != null ? finishedTs.toInstant() : null;
                    String statusVal = rs.getString("status");
                    String eventsJson = rs.getString("events_json");
                    List<AgentTraceEvent> events = deserializeEvents(eventsJson);
                    return new AgentTraceRun(traceIdVal, chatIdVal, startedAtVal, finishedAtVal, statusVal, events);
                },
                traceId);
    }

    public List<AgentTraceRun> findAllRecent(int limit) {
        return jdbcTemplate.query("""
            SELECT request_id, trace_id, chat_id, status, started_at, finished_at, events_json
            FROM ai_request_trace
            ORDER BY started_at DESC
            LIMIT ?
            """,
                (rs, rowNum) -> {
                    String traceIdVal = rs.getString("request_id");
                    String chatIdVal = rs.getString("chat_id");
                    Instant startedAtVal = rs.getTimestamp("started_at").toInstant();
                    Timestamp finishedTs = rs.getTimestamp("finished_at");
                    Instant finishedAtVal = finishedTs != null ? finishedTs.toInstant() : null;
                    String statusVal = rs.getString("status");
                    String eventsJson = rs.getString("events_json");
                    List<AgentTraceEvent> events = deserializeEvents(eventsJson);
                    return new AgentTraceRun(traceIdVal, chatIdVal, startedAtVal, finishedAtVal, statusVal, events);
                },
                limit);
    }

    public void deleteByTraceId(String traceId) {
        jdbcTemplate.update("DELETE FROM ai_request_trace WHERE request_id = ?", traceId);
    }

    private List<AgentTraceEvent> deserializeEvents(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<AgentTraceEvent> result = objectMapper.readValue(json, EVENT_LIST_TYPE);
            return result != null ? result : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize events JSON", e);
            return Collections.emptyList();
        }
    }
}
