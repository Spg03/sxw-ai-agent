package com.sxw.sxwaiagent.hermes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.evaluation.EvalCaseType;
import com.sxw.sxwaiagent.evaluation.EvalService;
import com.sxw.sxwaiagent.evaluation.ValidationMode;
import com.sxw.sxwaiagent.knowledge.DocumentIngestService;
import com.sxw.sxwaiagent.memory.MemoryItem;
import com.sxw.sxwaiagent.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hermes 应用器
 * <p>
 * 将审核通过的候选应用到对应模块（Memory、Knowledge、Eval 等）。
 */
@Component
public class HermesApplier {

    private static final Logger log = LoggerFactory.getLogger(HermesApplier.class);

    private final MemoryService memoryService;
    private final DocumentIngestService documentIngestService;
    private final EvalService evalService;
    private final ObjectMapper objectMapper;

    public HermesApplier(MemoryService memoryService,
                         DocumentIngestService documentIngestService,
                         EvalService evalService,
                         ObjectMapper objectMapper) {
        this.memoryService = memoryService;
        this.documentIngestService = documentIngestService;
        this.evalService = evalService;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用候选
     *
     * @param candidate 待应用的候选
     * @return 应用结果描述
     */
    public String apply(HermesCandidate candidate) {
        if (!candidate.canApply()) {
            throw new IllegalStateException("Candidate cannot be applied: " + candidate.status());
        }

        return switch (candidate.candidateType()) {
            case MEMORY -> applyMemory(candidate);
            case KNOWLEDGE -> applyKnowledge(candidate);
            case EVAL_CASE -> applyEvalCase(candidate);
            case AGENT_RULE -> applyAgentRule(candidate);
            case PROMPT_IMPROVEMENT, PROMPT_HINT -> applyPromptImprovement(candidate);
            case TOOL_IMPROVEMENT, TOOL_PATTERN -> applyToolImprovement(candidate);
            case DOC_UPDATE -> applyDocUpdate(candidate);
        };
    }

    /**
     * 应用记忆候选：通过 MemoryService 统一门面写入
     */
    private String applyMemory(HermesCandidate candidate) {
        MemoryItem memoryItem = memoryService.upsert(candidate);
        log.info("Applied memory candidate: {} -> {}", candidate.candidateId(), memoryItem.memoryId());
        return "Memory created: " + memoryItem.memoryId();
    }

    /**
     * 应用知识候选：通过 DocumentIngestService 写入知识库（内容哈希去重保证幂等）
     */
    private String applyKnowledge(HermesCandidate candidate) {
        String title = "[Hermes] " + candidate.title();
        String sourcePath = "hermes://" + candidate.candidateId();

        DocumentIngestService.IngestResult result =
                documentIngestService.ingest(title, sourcePath, candidate.content());

        if (result.isSuccess()) {
            log.info("Applied knowledge candidate: {} -> doc {}",
                    candidate.candidateId(), result.docId());
            return "Knowledge ingested: docId=" + result.docId() + ", chunks=" + result.chunkCount();
        } else {
            log.warn("Knowledge ingest skipped/failed for candidate {}: {}",
                    candidate.candidateId(), result.message());
            return "Knowledge ingest result: " + result.message();
        }
    }

    /**
     * 应用评测用例候选：通过 EvalService 创建 Case
     * 要求 metadata 含 inputPrompt / expectedOutput，缺失则拒绝
     */
    private String applyEvalCase(HermesCandidate candidate) {
        Map<String, Object> meta = parseMetadata(candidate.metadata());

        String inputPrompt = meta.containsKey("inputPrompt")
                ? String.valueOf(meta.get("inputPrompt")) : null;
        String expectedOutput = meta.containsKey("expectedOutput")
                ? String.valueOf(meta.get("expectedOutput")) : null;

        if (inputPrompt == null || inputPrompt.isBlank()) {
            log.warn("Eval case candidate {} missing inputPrompt in metadata, recording as suggestion",
                    candidate.candidateId());
            return "已记录评测建议（缺少 inputPrompt，未创建 Case）";
        }

        evalService.createCase(
                candidate.title(),
                EvalCaseType.CONVERSATION,
                null,  // profileCode 由用户后续配置
                inputPrompt,
                expectedOutput != null ? expectedOutput : "",
                null,
                ValidationMode.KEYWORD_ONLY,
                "hermes-applier"
        );

        log.info("Applied eval case candidate: {}", candidate.candidateId());
        return "Eval case created: " + candidate.title();
    }

    /**
     * 应用 Agent 规则候选：通过 MemoryService 统一门面写入
     */
    private String applyAgentRule(HermesCandidate candidate) {
        MemoryItem ruleItem = memoryService.upsert(candidate);
        log.info("Applied agent rule candidate: {} -> {}", candidate.candidateId(), ruleItem.memoryId());
        return "Agent rule saved as memory: " + ruleItem.memoryId();
    }

    /**
     * Prompt 改进建议：记录到 metadata，不自动应用
     */
    private String applyPromptImprovement(HermesCandidate candidate) {
        log.info("Prompt improvement suggestion recorded: {} - {}",
                candidate.candidateId(), candidate.title());
        return "已记录 Prompt 改进建议，待人工审核后手动应用";
    }

    /**
     * 工具改进建议：记录到 metadata，不自动应用
     */
    private String applyToolImprovement(HermesCandidate candidate) {
        log.info("Tool improvement suggestion recorded: {} - {}",
                candidate.candidateId(), candidate.title());
        return "已记录工具改进建议，待人工审核后手动应用";
    }

    /**
     * 文档更新建议：记录到 metadata，不自动应用
     */
    private String applyDocUpdate(HermesCandidate candidate) {
        log.info("Doc update suggestion recorded: {} - {}",
                candidate.candidateId(), candidate.title());
        return "已记录文档更新建议，待人工审核后手动应用";
    }

    // ───────────────────── Internal helpers ─────────────────────

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse candidate metadata: {}", e.getMessage());
            return Map.of();
        }
    }
}
