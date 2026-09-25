package com.sxw.sxwaiagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/** Reliable outbox worker with short leases, retries and dead-letter state. */
@Component
public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ConversationSummaryService summaries;
    private final WorkingMemoryService working;
    private final MemoryEmbeddingService embeddings;
    private final TransactionTemplate transactions;

    @Value("${sxw.agent.memory.outbox-max-attempts:5}")
    private int maxAttempts;

    public OutboxWorker(JdbcTemplate jdbc, ObjectMapper json, ConversationSummaryService summaries,
                        WorkingMemoryService working, MemoryEmbeddingService embeddings,
                        PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.summaries = summaries;
        this.working = working;
        this.embeddings = embeddings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${sxw.agent.memory.outbox-delay-ms:5000}")
    public void poll() {
        try {
            recoverStaleLeases();
            for (int index = 0; index < 10; index++) {
                Row row = claim();
                if (row == null) return;
                process(row);
            }
        } catch (Exception e) {
            log.warn("Memory outbox poll failed: {}", e.getMessage());
        }
    }

    private Row claim() {
        return transactions.execute(status -> {
            List<Row> rows = jdbc.query("""
                    SELECT id,event_type,aggregate_id,payload::text,attempt_count
                      FROM ai_outbox_event
                     WHERE status='PENDING' AND next_retry_at<=NOW()
                     ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs, rowNum) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getInt(5) + 1));
            if (rows.isEmpty()) return null;
            Row row = rows.getFirst();
            jdbc.update("UPDATE ai_outbox_event SET status='RUNNING',locked_at=NOW(),"
                    + "attempt_count=attempt_count+1 WHERE id=?", row.id());
            return row;
        });
    }

    private void process(Row row) {
        try {
            Map<String, Object> payload = json.readValue(row.payload(), new TypeReference<>() { });
            switch (row.type()) {
                case "SUMMARY_REQUESTED" -> summaries.materialize(row.aggregateId(),
                        ((Number) payload.get("covered")).longValue(), ((Number) payload.get("end")).longValue());
                case "WORKING_MEMORY_REQUESTED" -> working.materialize(row.aggregateId(),
                        ((Number) payload.get("end")).longValue());
                case "MEMORY_EMBEDDING_REQUESTED" -> embeddings.index(
                        (String) payload.get("memoryId"), (String) payload.get("content"));
                case "MEMORY_PURGE_REQUESTED" -> embeddings.purge(
                        (String) payload.get("memoryId"), ((Number) payload.get("userId")).longValue());
                default -> throw new IllegalArgumentException("Unsupported outbox event: " + row.type());
            }
            jdbc.update("UPDATE ai_outbox_event SET status='SUCCEEDED',completed_at=NOW(),locked_at=NULL,last_error=NULL WHERE id=?",
                    row.id());
        } catch (Exception e) {
            String error = (e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            if (error.length() > 2000) error = error.substring(0, 2000);
            jdbc.update("UPDATE ai_outbox_event SET status=CASE WHEN attempt_count>=? THEN 'DEAD' ELSE 'PENDING' END,"
                            + "next_retry_at=NOW() + (INTERVAL '15 seconds' * LEAST(attempt_count * attempt_count, 20)),"
                            + "locked_at=NULL,last_error=? WHERE id=?",
                    maxAttempts, error, row.id());
            log.warn("Outbox event {} ({}) failed on attempt {}: {}", row.id(), row.type(), row.attempt(), error);
        }
    }

    private void recoverStaleLeases() {
        int recovered = jdbc.update("UPDATE ai_outbox_event SET status='PENDING',locked_at=NULL,"
                + "next_retry_at=NOW(),last_error=COALESCE(last_error,'') || ' [stale lease recovered]' "
                + "WHERE status='RUNNING' AND locked_at<NOW()-INTERVAL '10 minutes'");
        if (recovered > 0) log.warn("Recovered {} stale memory outbox leases", recovered);
    }

    private record Row(long id, String type, String aggregateId, String payload, int attempt) { }
}
