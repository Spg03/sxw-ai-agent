package com.sxw.sxwaiagent.infrastructure.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of DbAgentTraceRepository for unit tests.
 */
public class InMemoryAgentTraceRepository extends DbAgentTraceRepository {

    private final Map<String, TraceRow> rows = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public InMemoryAgentTraceRepository() {
        super(null, null);
    }

    @Override
    public void saveRun(String traceId, String chatId, Instant startedAt, String status) {
        rows.put(traceId, new TraceRow(traceId, traceId, chatId, status, startedAt, null, null, sequence.incrementAndGet()));
    }

    @Override
    public void updateEvents(String traceId, List<AgentTraceEvent> events) {
        TraceRow row = rows.get(traceId);
        if (row != null) {
            rows.put(traceId, new TraceRow(row.requestId, row.traceId, row.chatId, row.status,
                    row.startedAt, row.finishedAt, new ArrayList<>(events), row.seq));
        }
    }

    @Override
    public void updateStatus(String traceId, String status, Instant finishedAt) {
        TraceRow row = rows.get(traceId);
        if (row != null) {
            rows.put(traceId, new TraceRow(row.requestId, row.traceId, row.chatId, status,
                    row.startedAt, finishedAt, row.events, row.seq));
        }
    }

    @Override
    public List<AgentTraceRun> findRecentByChatId(String chatId, int limit) {
        return rows.values().stream()
                .filter(r -> chatId.equals(r.chatId))
                .sorted(Comparator.comparing((TraceRow r) -> r.startedAt)
                        .thenComparingLong(r -> r.seq).reversed())
                .limit(limit)
                .map(r -> new AgentTraceRun(r.requestId, r.chatId, r.startedAt, r.finishedAt,
                        r.status, r.events != null ? List.copyOf(r.events) : List.of()))
                .toList();
    }

    @Override
    public Set<String> findExistingTraceIds(String chatId) {
        return rows.values().stream()
                .filter(r -> chatId.equals(r.chatId))
                .map(r -> r.requestId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public List<String> findAllTraceIdsByChatId(String chatId) {
        return rows.values().stream()
                .filter(r -> chatId.equals(r.chatId))
                .sorted(Comparator.comparing((TraceRow r) -> r.startedAt)
                        .thenComparingLong(r -> r.seq))
                .map(r -> r.requestId)
                .toList();
    }

    @Override
    public List<AgentTraceRun> findRecentByTraceId(String traceId) {
        TraceRow row = rows.get(traceId);
        if (row == null) return List.of();
        return List.of(new AgentTraceRun(row.requestId, row.chatId, row.startedAt, row.finishedAt,
                row.status, row.events != null ? List.copyOf(row.events) : List.of()));
    }

    @Override
    public void deleteByTraceId(String traceId) {
        rows.remove(traceId);
    }

    private record TraceRow(String requestId, String traceId, String chatId, String status,
                            Instant startedAt, Instant finishedAt, List<AgentTraceEvent> events,
                            long seq) {}
}
