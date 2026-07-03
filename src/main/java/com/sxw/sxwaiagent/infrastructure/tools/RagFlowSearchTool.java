package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.infrastructure.rag.RagFlowKnowledgeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Manus tool for querying the configured RAGFlow knowledge base.
 */
public class RagFlowSearchTool {

    private static final int MAX_OUTPUT_CHARS = 12_000;

    private final RagFlowKnowledgeService ragFlowKnowledgeService;

    public RagFlowSearchTool(RagFlowKnowledgeService ragFlowKnowledgeService) {
        this.ragFlowKnowledgeService = ragFlowKnowledgeService;
    }

    @Tool(description = "Search the configured RAGFlow knowledge base and return relevant chunks")
    public String searchRagFlow(
            @ToolParam(description = "Question or keywords to retrieve from RAGFlow") String query) {
        if (query == null || query.isBlank()) {
            return "failed: query must not be blank";
        }
        String context = ragFlowKnowledgeService.retrieveContext(query);
        if (context == null || context.isBlank()) {
            return "ok: no relevant chunks";
        }
        return ToolSandboxSupport.limitOutput("ok:\n" + context, MAX_OUTPUT_CHARS);
    }
}
