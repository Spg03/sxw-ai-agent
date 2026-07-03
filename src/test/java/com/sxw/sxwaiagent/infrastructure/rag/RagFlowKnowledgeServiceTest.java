package com.sxw.sxwaiagent.infrastructure.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFlowKnowledgeServiceTest {

    @Test
    void retrieveContextReturnsEmptyWhenDisabled() {
        RagFlowProperties properties = new RagFlowProperties();
        properties.setEnabled(false);
        RagFlowKnowledgeService service = new RagFlowKnowledgeService(query -> {
            throw new AssertionError("client should not be called");
        }, properties);

        assertEquals("", service.retrieveContext("question"));
    }

    @Test
    void retrieveContextFormatsChunksForPrompt() {
        RagFlowProperties properties = enabledProperties();
        RagFlowKnowledgeService service = new RagFlowKnowledgeService(query -> new RagFlowClient.RetrievalResult(
                List.of(new RagFlowClient.Chunk(
                        "chunk-1",
                        "Use calm, concrete communication and ask for the other person's perspective.",
                        "doc-1",
                        "love.md",
                        "kb-1",
                        0.91,
                        0.82,
                        0.73
                )),
                List.of(new RagFlowClient.DocAgg("doc-1", "love.md", 1)),
                1
        ), properties);

        String context = service.retrieveContext("communication");

        assertTrue(context.contains("RAGFlow"), context);
        assertTrue(context.contains("love.md"), context);
        assertTrue(context.contains("Use calm"), context);
    }

    @Test
    void retrieveContextReturnsEmptyWhenClientFails() {
        RagFlowProperties properties = enabledProperties();
        RagFlowKnowledgeService service = new RagFlowKnowledgeService(query -> {
            throw new IllegalStateException("boom");
        }, properties);

        assertEquals("", service.retrieveContext("question"));
    }

    private static RagFlowProperties enabledProperties() {
        RagFlowProperties properties = new RagFlowProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:9380");
        properties.setApiKey("test-key");
        properties.setDatasetIds(List.of("dataset-1"));
        return properties;
    }
}
