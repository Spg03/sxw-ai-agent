package com.sxw.sxwaiagent.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MemoryRetrievalSnapshotStoreTest {

    @Test
    void freezesHitsForTheWholeRequestAndCanBeInvalidated() {
        MemoryRetrievalSnapshotStore store = new MemoryRetrievalSnapshotStore();
        AtomicInteger loads = new AtomicInteger();

        UserMemoryService.RetrievalSnapshot first = store.getOrCreate("request-1", () -> snapshot(loads.incrementAndGet()));
        UserMemoryService.RetrievalSnapshot second = store.getOrCreate("request-1", () -> snapshot(loads.incrementAndGet()));

        assertSame(first, second);
        assertEquals(1, loads.get());

        store.invalidate("request-1");
        UserMemoryService.RetrievalSnapshot refreshed = store.getOrCreate("request-1", () -> snapshot(loads.incrementAndGet()));
        assertNotEquals(first.snapshotId(), refreshed.snapshotId());
        assertEquals(2, loads.get());
    }

    private UserMemoryService.RetrievalSnapshot snapshot(int version) {
        return new UserMemoryService.RetrievalSnapshot("snapshot-" + version, Instant.now(),
                List.of(), List.of(), 0, 0);
    }
}
