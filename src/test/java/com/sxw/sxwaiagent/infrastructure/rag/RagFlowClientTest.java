package com.sxw.sxwaiagent.infrastructure.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagFlowClientTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void retrieveChunksPostsConfiguredRequestAndParsesResponse() throws Exception {
        String json = """
                {
                  "code": 0,
                  "data": {
                    "chunks": [
                      {
                        "id": "chunk-1",
                        "content": "RAGFlow can retrieve grounded relationship advice.",
                        "document_id": "doc-1",
                        "document_keyword": "love.md",
                        "kb_id": "kb-1",
                        "similarity": 0.91,
                        "vector_similarity": 0.82,
                        "term_similarity": 0.73
                      }
                    ],
                    "doc_aggs": [
                      {"doc_id": "doc-1", "doc_name": "love.md", "count": 1}
                    ],
                    "total": 1
                  }
                }
                """;
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        StringBuilder requestBody = new StringBuilder();

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/retrieval", exchange -> {
            requestBody.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        RagFlowProperties properties = new RagFlowProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setDatasetIds(List.of("dataset-1"));
        properties.setPageSize(3);
        properties.setSimilarityThreshold(0.5);
        properties.setVectorSimilarityWeight(0.7);
        properties.setTopK(12);
        properties.setKeyword(true);

        RagFlowClient client = new RagFlowClient(properties);
        RagFlowClient.RetrievalResult result = client.retrieve("how should I communicate?");

        assertTrue(requestBody.toString().contains("\"question\":\"how should I communicate?\""), requestBody.toString());
        assertTrue(requestBody.toString().contains("\"dataset_ids\":[\"dataset-1\"]"), requestBody.toString());
        assertEquals(1, result.total());
        assertEquals("chunk-1", result.chunks().get(0).id());
        assertEquals("love.md", result.docAggs().get(0).docName());
    }

    @Test
    void retrieveChunksThrowsWhenRagFlowReturnsBusinessError() throws Exception {
        String json = "{\"code\":102,\"message\":\"`datasets` is required.\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/retrieval", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        RagFlowProperties properties = new RagFlowProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        properties.setApiKey("test-key");
        properties.setDatasetIds(List.of("dataset-1"));

        RagFlowClient client = new RagFlowClient(properties);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> client.retrieve("x"));

        assertTrue(thrown.getMessage().contains("datasets"), thrown.getMessage());
    }
}
