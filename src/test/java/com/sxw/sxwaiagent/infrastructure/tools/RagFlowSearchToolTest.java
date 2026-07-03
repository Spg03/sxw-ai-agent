package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.infrastructure.rag.RagFlowKnowledgeService;
import com.sxw.sxwaiagent.infrastructure.rag.RagFlowProperties;
import com.sxw.sxwaiagent.infrastructure.rag.RagFlowClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFlowSearchToolTest {

    @Test
    void searchRagFlowReturnsFormattedContext() {
        RagFlowKnowledgeService service = new RagFlowKnowledgeService(
                query -> new RagFlowClient.RetrievalResult(
                        List.of(new RagFlowClient.Chunk(
                                "chunk-1",
                                "RAGFlow context for " + query,
                                "doc-1",
                                "love.md",
                                "kb-1",
                                0.9,
                                0.8,
                                0.7
                        )),
                        List.of(new RagFlowClient.DocAgg("doc-1", "love.md", 1)),
                        1
                ),
                enabledProperties()
        );
        RagFlowSearchTool tool = new RagFlowSearchTool(service);

        String result = tool.searchRagFlow("relationship conflict");

        assertTrue(result.startsWith("ok:"), result);
        assertTrue(result.contains("relationship conflict"), result);
    }

    @Test
    void searchRagFlowReturnsNoMatchesWhenContextIsBlank() {
        RagFlowKnowledgeService service = new RagFlowKnowledgeService(
                query -> new RagFlowClient.RetrievalResult(List.of(), List.of(), 0),
                enabledProperties()
        );
        RagFlowSearchTool tool = new RagFlowSearchTool(service);

        String result = tool.searchRagFlow("unknown");

        assertTrue(result.contains("no relevant chunks"), result);
    }

    private static RagFlowProperties enabledProperties() {
        RagFlowProperties properties = new RagFlowProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:9380");
        properties.setApiKey("test-key");
        properties.setDatasetIds(java.util.List.of("dataset-1"));
        return properties;
    }
}
