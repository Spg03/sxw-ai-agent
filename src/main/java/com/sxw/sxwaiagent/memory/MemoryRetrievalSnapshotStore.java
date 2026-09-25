package com.sxw.sxwaiagent.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/** Freezes long-term memory hits for one request, including all tool-loop calls. */
@Component
public class MemoryRetrievalSnapshotStore {
    private final Cache<String, UserMemoryService.RetrievalSnapshot> snapshots = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    public UserMemoryService.RetrievalSnapshot getOrCreate(
            String requestId, Supplier<UserMemoryService.RetrievalSnapshot> loader) {
        if (requestId == null || requestId.isBlank()) return loader.get();
        return snapshots.get(requestId, ignored -> loader.get());
    }

    public void invalidate(String requestId) {
        if (requestId != null) snapshots.invalidate(requestId);
    }
}
