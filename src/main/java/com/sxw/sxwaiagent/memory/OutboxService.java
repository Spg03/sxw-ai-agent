package com.sxw.sxwaiagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class OutboxService {
    private static final String INSERT_SQL = """
            INSERT INTO ai_outbox_event(event_type,aggregate_type,aggregate_id,payload,idempotency_key)
            VALUES (?,?,?,?::jsonb,?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void enqueue(String type, String aggregateType, String aggregateId, String key,
                        Map<String, Object> payload) {
        try {
            jdbc.update(INSERT_SQL, type, aggregateType, aggregateId,
                    json.writeValueAsString(payload), key);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to enqueue background event", error);
        }
    }
}
