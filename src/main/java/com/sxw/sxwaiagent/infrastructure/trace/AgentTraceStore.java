package com.sxw.sxwaiagent.infrastructure.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@EnableConfigurationProperties(AgentTraceProperties.class)
public class AgentTraceStore {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceStore.class);

    private final AgentTraceProperties properties;
    private final DbAgentTraceRepository repository;

    public AgentTraceStore(AgentTraceProperties properties, DbAgentTraceRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public String startRun(String chatId) {
        String safeChatId = safe(chatId);
        String traceId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            repository.saveRun(traceId, safeChatId, now, "running");
            trimRuns(safeChatId);
        } catch (RuntimeException e) {
            log.error("Failed to persist trace start for chatId={}", safeChatId, e);
        }
        return traceId;
    }

    public void appendEvent(String traceId,
                            int step,
                            String phase,
                            String toolName,
                            String inputSummary,
                            String outputSummary,
                            String status,
                            long latencyMs) {
        AgentTraceEvent event = new AgentTraceEvent(
                traceId,
                "",
                step,
                safe(phase),
                safe(toolName),
                abbreviate(inputSummary),
                abbreviate(outputSummary),
                safe(status),
                Math.max(0, latencyMs),
                Instant.now()
        );
        try {
            persistEventForTrace(traceId, event);
        } catch (RuntimeException e) {
            log.error("Failed to persist event for trace {}", traceId, e);
        }
    }

    private void persistEventForTrace(String traceId, AgentTraceEvent newEvent) {
        List<AgentTraceRun> runs = repository.findRecentByTraceId(traceId);
        Deque<AgentTraceEvent> events = new ArrayDeque<>();
        if (!runs.isEmpty() && runs.get(0).events() != null) {
            events.addAll(runs.get(0).events());
        }
        events.addLast(newEvent);
        trimEvents(events);
        repository.updateEvents(traceId, new ArrayList<>(events));
    }

    public void finishRun(String traceId, String status) {
        try {
            List<AgentTraceRun> runs = repository.findRecentByTraceId(traceId);
            if (runs.isEmpty()) {
                return;
            }
            AgentTraceRun run = runs.get(0);
            if (run.finishedAt() != null) {
                return;
            }
            Instant finishedAt = Instant.now();
            String safeStatus = safe(status);

            AgentTraceEvent lastEvent = (run.events() != null && !run.events().isEmpty())
                    ? run.events().get(run.events().size() - 1)
                    : null;
            int step = lastEvent == null ? 0 : lastEvent.step();
            AgentTraceEvent finishEvent = new AgentTraceEvent(
                    traceId, run.chatId(), step, "finish", "", "", "", safeStatus, 0, finishedAt);

            Deque<AgentTraceEvent> events = new ArrayDeque<>();
            if (run.events() != null) {
                events.addAll(run.events());
            }
            events.addLast(finishEvent);
            trimEvents(events);

            repository.updateEvents(traceId, new ArrayList<>(events));
            repository.updateStatus(traceId, safeStatus, finishedAt);
        } catch (RuntimeException e) {
            log.error("Failed to finish trace {}", traceId, e);
        }
    }

    public List<AgentTraceRun> recentRuns(String chatId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, Math.max(1, properties.getMaxRuns())));
        try {
            return repository.findRecentByChatId(safe(chatId), safeLimit);
        } catch (RuntimeException e) {
            log.error("Failed to query recent runs for chatId={}", chatId, e);
            return List.of();
        }
    }

    public List<AgentTraceRun> findAllRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try {
            return repository.findAllRecent(safeLimit);
        } catch (RuntimeException e) {
            log.error("Failed to query all recent traces", e);
            return List.of();
        }
    }

    public List<AgentTraceRun> findByTraceId(String traceId) {
        try {
            return repository.findRecentByTraceId(traceId);
        } catch (RuntimeException e) {
            log.error("Failed to query trace by traceId={}", traceId, e);
            return List.of();
        }
    }

    private void trimRuns(String chatId) {
        int maxRuns = Math.max(1, properties.getMaxRuns());
        try {
            Set<String> traceIds = repository.findExistingTraceIds(chatId);
            if (traceIds.size() > maxRuns) {
                List<String> allIds = repository.findAllTraceIdsByChatId(chatId);
                int toRemove = allIds.size() - maxRuns;
                for (int i = 0; i < toRemove; i++) {
                    repository.deleteByTraceId(allIds.get(i));
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to trim runs for chatId={}", chatId, e);
        }
    }

    private void trimEvents(Deque<AgentTraceEvent> events) {
        int maxEvents = Math.max(1, properties.getMaxEventsPerRun());
        while (events.size() > maxEvents) {
            events.removeFirst();
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
}
