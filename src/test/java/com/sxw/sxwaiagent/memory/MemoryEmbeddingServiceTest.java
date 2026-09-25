package com.sxw.sxwaiagent.memory;

import com.sxw.sxwaiagent.knowledge.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryEmbeddingServiceTest {

    @Test
    void doesNotRecreateEmbeddingForDeletedMemory() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("memory-1"))).thenReturn(0);

        new MemoryEmbeddingService(jdbc, embeddings).index("memory-1", "preference");

        verifyNoInteractions(embeddings);
        verify(jdbc, never()).update(startsWith("INSERT INTO ai_memory_embedding"), any(Object[].class));
    }

    @Test
    void removesJustCreatedEmbeddingWhenMemoryIsPurgedDuringGeneration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("memory-1"))).thenReturn(1);
        when(embeddings.embed(anyString())).thenReturn(new float[1024]);
        when(jdbc.update(startsWith("UPDATE ai_memory_item SET active_embedding_id"), any(Object[].class)))
                .thenReturn(0);

        new MemoryEmbeddingService(jdbc, embeddings).index("memory-1", "preference");

        verify(jdbc).update(startsWith("DELETE FROM ai_memory_embedding"), eq("memory-1"), anyString());
    }
}
