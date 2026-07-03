package com.sxw.sxwaiagent.infrastructure.rag;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RAGFlow HTTP client for POST /api/v1/retrieval.
 */
public class RagFlowClient {

    private final RagFlowProperties properties;
    private final HttpClient httpClient;

    public RagFlowClient(RagFlowProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build());
    }

    RagFlowClient(RagFlowProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public RetrievalResult retrieve(String question) {
        String body = buildRequestBody(question);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl()))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("RAGFlow retrieval HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("RAGFlow retrieval request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RAGFlow retrieval request interrupted", e);
        }
    }

    private String buildRequestBody(String question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("dataset_ids", properties.getDatasetIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .toList());
        payload.put("page", Math.max(1, properties.getPage()));
        payload.put("page_size", Math.max(1, properties.getPageSize()));
        payload.put("similarity_threshold", properties.getSimilarityThreshold());
        payload.put("vector_similarity_weight", properties.getVectorSimilarityWeight());
        payload.put("top_k", Math.max(1, properties.getTopK()));
        payload.put("keyword", properties.isKeyword());
        payload.put("highlight", false);
        return JSONUtil.toJsonStr(payload);
    }

    private RetrievalResult parseResponse(String body) {
        JSONObject root = JSONUtil.parseObj(body);
        int code = intValue(root.get("code"), -1);
        if (code != 0) {
            throw new IllegalStateException("RAGFlow retrieval failed: " + root.getStr("message", "unknown error"));
        }
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return new RetrievalResult(List.of(), List.of(), 0);
        }
        List<Chunk> chunks = new ArrayList<>();
        JSONArray chunkArray = data.getJSONArray("chunks");
        if (chunkArray != null) {
            for (Object item : chunkArray) {
                JSONObject chunk = JSONUtil.parseObj(item);
                chunks.add(new Chunk(
                        chunk.getStr("id", ""),
                        chunk.getStr("content", ""),
                        chunk.getStr("document_id", ""),
                        chunk.getStr("document_keyword", ""),
                        chunk.getStr("kb_id", ""),
                        doubleValue(chunk.get("similarity")),
                        doubleValue(chunk.get("vector_similarity")),
                        doubleValue(chunk.get("term_similarity"))
                ));
            }
        }
        List<DocAgg> docAggs = new ArrayList<>();
        JSONArray docAggArray = data.getJSONArray("doc_aggs");
        if (docAggArray != null) {
            for (Object item : docAggArray) {
                JSONObject docAgg = JSONUtil.parseObj(item);
                docAggs.add(new DocAgg(
                        docAgg.getStr("doc_id", ""),
                        docAgg.getStr("doc_name", ""),
                        intValue(docAgg.get("count"), 0)
                ));
            }
        }
        return new RetrievalResult(chunks, docAggs, intValue(data.get("total"), chunks.size()));
    }

    private String endpointUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/v1/retrieval";
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleValue(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public record RetrievalResult(List<Chunk> chunks, List<DocAgg> docAggs, int total) {
    }

    public record Chunk(String id,
                        String content,
                        String documentId,
                        String documentKeyword,
                        String kbId,
                        double similarity,
                        double vectorSimilarity,
                        double termSimilarity) {
    }

    public record DocAgg(String docId, String docName, int count) {
    }
}
