package com.sxw.sxwaiagent.memory;

import com.sxw.sxwaiagent.context.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UserMemoryServiceTest {

    @Test
    void nullableScopesRetainGlobalAndAgentMemoriesWithoutCrossUserOrProjectLeaks() {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE ai_memory_item(memory_id VARCHAR, user_id BIGINT, name VARCHAR, description VARCHAR, "
                + "priority VARCHAR, scope_type VARCHAR, scope_id VARCHAR, status VARCHAR, always_on BOOLEAN, "
                + "deleted_at TIMESTAMP, expired_at TIMESTAMP, activated_at TIMESTAMP)");
        jdbc.update("INSERT INTO ai_memory_item(memory_id,user_id,name,description,priority,scope_type,scope_id,status,always_on) VALUES "
                + "('global',1,'global','preference','NORMAL','GLOBAL',NULL,'ACTIVE',true),"
                + "('agent',1,'agent','preference','USER_PINNED','AGENT','GENERAL','ACTIVE',true),"
                + "('project',1,'project','preference','NORMAL','PROJECT','p1','ACTIVE',true),"
                + "('relationship',1,'relationship','preference','NORMAL','RELATIONSHIP','r1','ACTIVE',true),"
                + "('other-user',2,'other','preference','NORMAL','GLOBAL',NULL,'ACTIVE',true)");
        UserMemoryService service = new UserMemoryService(jdbc, mock(OutboxService.class),
                mock(com.sxw.sxwaiagent.knowledge.EmbeddingService.class), new TokenCounter());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "alwaysOnTokenBudget", 1000);

        var withoutScopes = service.snapshot(1, "", "GENERAL", null, null);
        assertEquals(java.util.Set.of("global", "agent"), withoutScopes.alwaysOn().stream()
                .map(UserMemoryService.MemoryHit::memoryId).collect(java.util.stream.Collectors.toSet()));
        var withScopes = service.snapshot(1, "", "GENERAL", "p1", "r1");
        assertEquals(4, withScopes.alwaysOn().size());
    }

    @Test
    void appliesBothItemAndTokenLimitsWithoutSplittingMemoryEntries() {
        TokenCounter tokenCounter = new TokenCounter();
        UserMemoryService service = new UserMemoryService(mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(OutboxService.class), mock(com.sxw.sxwaiagent.knowledge.EmbeddingService.class), tokenCounter);
        List<UserMemoryService.MemoryHit> input = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new UserMemoryService.MemoryHit("m" + index, "preference " + index,
                        "concise answer ".repeat(8), "NORMAL", "GLOBAL", null, 1.0, 0))
                .toList();

        List<UserMemoryService.MemoryHit> limited = service.fit(input, 5, 80);

        assertTrue(limited.size() <= 5);
        assertTrue(limited.stream().mapToInt(UserMemoryService.MemoryHit::tokens).sum() <= 80);
        assertEquals(input.getFirst().memoryId(), limited.getFirst().memoryId());
    }
}
