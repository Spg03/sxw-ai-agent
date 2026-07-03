package com.sxw.sxwaiagent.infrastructure.trace;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(AgentTraceProperties.class)
public class AgentTraceStore {

    private final AgentTraceProperties properties;
    private final Map<String, MutableRun> runs = new LinkedHashMap<>();
    private final Map<String, Deque<String>> runsByChatId = new LinkedHashMap<>();

    public AgentTraceStore(AgentTraceProperties properties) {
        this.properties = properties;
    }

    public synchronized String startRun(String chatId) {
        String safeChatId = safe(chatId);
        String traceId = UUID.randomUUID().toString();
        MutableRun run = new MutableRun(traceId, safeChatId, Instant.now());
        runs.put(traceId, run);
        runsByChatId.computeIfAbsent(safeChatId, key -> new ArrayDeque<>()).addLast(traceId);
        trimRuns(safeChatId);
        return traceId;
    }

    public synchronized void appendEvent(String traceId,
                                         int step,
                                         String phase,
                                         String toolName,
                                         String inputSummary,
                                         String outputSummary,
                                         String status,
                                         long latencyMs) {
        MutableRun run = runs.get(traceId);
        if (run == null) {
            return;
        }
        run.events.addLast(new AgentTraceEvent(
                traceId,
                run.chatId,
                step,
                safe(phase),
                safe(toolName),
                abbreviate(inputSummary),
                abbreviate(outputSummary),
                safe(status),
                Math.max(0, latencyMs),
                Instant.now()
        ));
        trimEvents(run);
    }

    public synchronized void finishRun(String traceId, String status) {
        MutableRun run = runs.get(traceId);
        if (run == null) {
            return;
        }
        if (run.finishedAt != null) {
            return;
        }
        run.finishedAt = Instant.now();
        run.status = safe(status);
        AgentTraceEvent lastEvent = run.events.peekLast();
        int step = lastEvent == null ? 0 : lastEvent.step();
        appendEvent(traceId, step, "finish", null, "", "", run.status, 0);
    }

    public synchronized List<AgentTraceRun> recentRuns(String chatId, int limit) {
        Deque<String> ids = runsByChatId.get(safe(chatId));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        List<String> orderedIds = new ArrayList<>(ids);
        Collections.reverse(orderedIds);
        return orderedIds.stream()
                .map(runs::get)
                .filter(run -> run != null)
                .limit(safeLimit)
                .map(MutableRun::snapshot)
                .toList();
    }

    private void trimRuns(String chatId) {
        int maxRuns = Math.max(1, properties.getMaxRuns());
        Deque<String> ids = runsByChatId.get(chatId);
        while (ids != null && ids.size() > maxRuns) {
            String removed = ids.removeFirst();
            runs.remove(removed);
        }
    }

    private void trimEvents(MutableRun run) {
        int maxEvents = Math.max(1, properties.getMaxEventsPerRun());
        while (run.events.size() > maxEvents) {
            run.events.removeFirst();
        }
    }

    private static String abbreviate(String value) {
        String safe = safe(value);
        int max = 1200;
        return safe.length() <= max ? safe : safe.substring(0, max) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class MutableRun {
        private final String traceId;
        private final String chatId;
        private final Instant startedAt;
        private Instant finishedAt;
        private String status = "running";
        private final Deque<AgentTraceEvent> events = new ArrayDeque<>();

        private MutableRun(String traceId, String chatId, Instant startedAt) {
            this.traceId = traceId;
            this.chatId = chatId;
            this.startedAt = startedAt;
        }

        private AgentTraceRun snapshot() {
            return new AgentTraceRun(traceId, chatId, startedAt, finishedAt, status, List.copyOf(new ArrayList<>(events)));
        }
    }
}
