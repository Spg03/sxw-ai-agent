package com.sxw.sxwaiagent.infrastructure.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TraceOwnershipTest {
    @Test void filtersByOwnerBeforeLimitAndHidesOrphanedTraces() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE ai_conversation(conversation_id VARCHAR(64) PRIMARY KEY, user_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_request_trace(request_id VARCHAR(64),trace_id VARCHAR(64),chat_id VARCHAR(64),status VARCHAR(32),started_at TIMESTAMP,finished_at TIMESTAMP,events_json TEXT)");
        jdbc.update("INSERT INTO ai_conversation VALUES ('alice-chat',1),('bob-chat',2)");
        jdbc.update("INSERT INTO ai_request_trace VALUES ('alice-trace','alice-trace','alice-chat','OK',TIMESTAMP '2026-01-01 00:00:00',NULL,'[]'),('bob-trace','bob-trace','bob-chat','OK',TIMESTAMP '2026-02-01 00:00:00',NULL,'[]'),('orphan','orphan','legacy-chat','OK',CURRENT_TIMESTAMP,NULL,'[]')");
        var repository = new DbAgentTraceRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        assertEquals("alice-trace", repository.findForUser(1,null,null,1).getFirst().traceId());
        assertTrue(repository.findForUser(1,"bob-chat",null,10).isEmpty());
        assertTrue(repository.findForUser(1,null,"bob-trace",1).isEmpty());
        assertTrue(repository.findForUser(1,null,"orphan",1).isEmpty());
        assertEquals(3, repository.findAllRecent(10).size());
        assertEquals("alice-trace", repository.findForUser(1,"alice-chat","alice-trace",1).getFirst().traceId());
    }
}
