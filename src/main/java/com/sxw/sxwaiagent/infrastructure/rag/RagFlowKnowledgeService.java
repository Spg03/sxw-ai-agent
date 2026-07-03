package com.sxw.sxwaiagent.infrastructure.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Objects;

/**
 * Converts RAGFlow retrieval results into prompt/tool friendly text.
 */
@Slf4j
public class RagFlowKnowledgeService {

    @FunctionalInterface
    public interface Retriever {
        RagFlowClient.RetrievalResult retrieve(String question);
    }

    private final Retriever retriever;
    private final RagFlowProperties properties;

    public RagFlowKnowledgeService(Retriever retriever, RagFlowProperties properties) {
        this.retriever = retriever;
        this.properties = properties;
    }

    public String retrieveContext(String question) {
        if (!properties.isConfigured() || question == null || question.isBlank()) {
            return "";
        }
        try {
            RagFlowClient.RetrievalResult result = retriever.retrieve(question);
            if (result == null || result.chunks() == null || result.chunks().isEmpty()) {
                return "";
            }
            return limit(format(result));
        } catch (Exception e) {
            log.warn("RAGFlow retrieval skipped: {}", e.getMessage());
            return "";
        }
    }

    private String format(RagFlowClient.RetrievalResult result) {
        StringBuilder out = new StringBuilder();
        out.append("RAGFlow knowledge base references:\n");
        result.chunks().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RagFlowClient.Chunk::similarity).reversed())
                .forEach(chunk -> {
                    int index = out.toString().split("\\n\\[").length;
                    out.append("\n[").append(index).append("] ");
                    String docName = docName(result, chunk);
                    if (!docName.isBlank()) {
                        out.append("document: ").append(docName).append(", ");
                    }
                    out.append("similarity: ").append(String.format(java.util.Locale.ROOT, "%.3f", chunk.similarity()));
                    out.append("\n").append(chunk.content()).append("\n");
                });
        return out.toString().trim();
    }

    private static String docName(RagFlowClient.RetrievalResult result, RagFlowClient.Chunk chunk) {
        String fromAgg = result.docAggs() == null ? "" : result.docAggs().stream()
                .filter(agg -> agg.docId().equals(chunk.documentId()))
                .map(RagFlowClient.DocAgg::docName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("");
        if (!fromAgg.isBlank()) {
            return fromAgg;
        }
        return chunk.documentKeyword() == null ? "" : chunk.documentKeyword();
    }

    private String limit(String text) {
        int maxChars = Math.max(1000, properties.getMaxContextChars());
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }
}
