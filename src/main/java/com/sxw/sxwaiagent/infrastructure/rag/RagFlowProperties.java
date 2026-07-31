package com.sxw.sxwaiagent.infrastructure.rag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * RAGFlow retrieval configuration.
 */
@Validated
@ConfigurationProperties(prefix = "sxw.ragflow")
public class RagFlowProperties {

    private boolean enabled = false;

    private String baseUrl = "http://localhost:9380";

    private String apiKey = "";

    private List<String> datasetIds = new ArrayList<>();

    @Min(value = 1, message = "page must be at least 1")
    private int page = 1;

    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 100, message = "pageSize must not exceed 100")
    private int pageSize = 5;

    private double similarityThreshold = 0.2;

    private double vectorSimilarityWeight = 0.3;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 10000, message = "topK must not exceed 10000")
    private int topK = 1024;

    private boolean keyword = false;

    @Min(value = 1, message = "timeoutSeconds must be at least 1")
    @Max(value = 120, message = "timeoutSeconds must not exceed 120")
    private int timeoutSeconds = 10;

    @Min(value = 100, message = "maxContextChars must be at least 100")
    @Max(value = 100000, message = "maxContextChars must not exceed 100000")
    private int maxContextChars = 12_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getDatasetIds() {
        return datasetIds;
    }

    public void setDatasetIds(List<String> datasetIds) {
        this.datasetIds = datasetIds == null ? new ArrayList<>() : datasetIds;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public double getVectorSimilarityWeight() {
        return vectorSimilarityWeight;
    }

    public void setVectorSimilarityWeight(double vectorSimilarityWeight) {
        this.vectorSimilarityWeight = vectorSimilarityWeight;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isKeyword() {
        return keyword;
    }

    public void setKeyword(boolean keyword) {
        this.keyword = keyword;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    @AssertTrue(message = "RAGFlow baseUrl must not be blank when ragflow is enabled")
    @JsonIgnore
    public boolean isValidBaseUrl() {
        return !enabled || hasText(baseUrl);
    }

    public boolean isConfigured() {
        return enabled
                && hasText(baseUrl)
                && hasText(apiKey)
                && datasetIds.stream().anyMatch(RagFlowProperties::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
