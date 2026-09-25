package com.sxw.sxwaiagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    @Test
    void serializesPayloadWithExplicitJsonbCastAndIdempotentInsert() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxService service = new OutboxService(jdbc, objectMapper);
        Map<String, Object> payload = Map.of("conversationId", "chat-1", "covered", 0, "end", 4);

        service.enqueue("SUMMARY_REQUESTED", "CONVERSATION", "chat-1", "summary-key", payload);
        service.enqueue("SUMMARY_REQUESTED", "CONVERSATION", "chat-1", "summary-key", payload);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("?::jsonb"));
        assertTrue(sql.getValue().contains("ON CONFLICT (idempotency_key) DO NOTHING"));

        Object[] values = parameters.getAllValues().getFirst();
        assertEquals("SUMMARY_REQUESTED", values[0]);
        assertEquals("summary-key", values[4]);
        assertEquals("chat-1", objectMapper.readTree((String) values[3]).get("conversationId").asText());
        assertEquals(4, objectMapper.readTree((String) values[3]).get("end").asInt());
    }
}
