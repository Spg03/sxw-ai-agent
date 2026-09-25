package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.knowledge.DocumentIngestService;
import com.sxw.sxwaiagent.knowledge.IndexConfig;
import com.sxw.sxwaiagent.knowledge.KnowledgeRepository;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalResult;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalService;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 知识库管理 API
 *
 * 提供文档入库、查询、删除等 REST 接口。
 */
@Tag(name = "知识库管理", description = "文档入库、查询、重新索引与删除")
@RestController
@RequestMapping("/api/knowledge")
@Validated
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final DocumentIngestService ingestService;
    private final KnowledgeRetrievalService retrievalService;
    private final IndexConfig indexConfig;

    public KnowledgeController(
        DocumentIngestService ingestService,
        KnowledgeRetrievalService retrievalService,
        @Value("${sxw.knowledge.embedding-model:text-embedding-v3}") String embeddingModel,
        @Value("${sxw.knowledge.embedding-model-version:1.0}") String embeddingModelVersion,
        @Value("${sxw.knowledge.chunk-size:1000}") int chunkSize,
        @Value("${sxw.knowledge.chunk-overlap:100}") int chunkOverlap,
        @Value("${sxw.knowledge.parser-version:1.0}") String parserVersion
    ) {
        this.ingestService = ingestService;
        this.retrievalService = retrievalService;
        this.indexConfig = new IndexConfig(embeddingModel, embeddingModelVersion,
            chunkSize, chunkOverlap, parserVersion);
    }

    @Operation(summary = "上传并入库文档", description = "上传 Markdown 或 TXT 文件，自动分块、向量化并入库")
    @PostMapping("/documents")
    public Result<DocumentIngestService.IngestResult> uploadDocument(Authentication authentication,
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "false") boolean force
    ) throws IOException {
        log.info("Uploading document: {} (force={})", file.getOriginalFilename(), force);

        String originalName = StringUtils.getFilename(StringUtils.cleanPath(
            file.getOriginalFilename() == null ? "knowledge.md" : file.getOriginalFilename()));
        if (originalName == null || originalName.isBlank()) {
            originalName = "knowledge.md";
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        DocumentIngestService.IngestResult result = ingestService.ingest(
            user(authentication), originalName, "upload:" + originalName, content, force, indexConfig);

        if (result.isSuccess()) {
            return Result.ok(result);
        } else {
            return Result.error(result.message());
        }
    }

    @Operation(summary = "从文本内容入库", description = "直接提交文本内容进行分块和向量化入库")
    @PostMapping("/documents/text")
    public Result<DocumentIngestService.IngestResult> ingestText(Authentication authentication,
        @RequestParam @NotBlank String title,
        @RequestParam(required = false) String sourcePath,
        @RequestParam(defaultValue = "false") boolean force,
        @RequestBody @NotBlank String content
    ) {
        log.info("Ingesting text document: {} (force={})", title, force);

        DocumentIngestService.IngestResult result = ingestService.ingest(user(authentication),
            title,
            sourcePath != null ? sourcePath : "text-input",
            content, force, indexConfig
        );

        if (result.isSuccess()) {
            return Result.ok(result);
        } else {
            return Result.error(result.message());
        }
    }

    @Operation(summary = "重新索引文档", description = "用新内容替换已有文档的分块和向量")
    @PutMapping("/documents/{docId}/content")
    public Result<DocumentIngestService.IngestResult> reindexDocument(Authentication authentication,
        @PathVariable String docId,
        @RequestBody @NotBlank String content
    ) {
        log.info("Reindexing document: {}", docId);
        try {
            DocumentIngestService.IngestResult result =
                ingestService.reindex(user(authentication), docId, content, indexConfig);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "列出所有文档", description = "返回知识库中所有已入库文档的元信息")
    @GetMapping("/documents")
    public Result<List<KnowledgeRepository.KnowledgeDocumentRecord>> listDocuments(Authentication authentication) {
        List<KnowledgeRepository.KnowledgeDocumentRecord> docs = ingestService.listDocuments(user(authentication));
        return Result.ok(docs);
    }

    @Operation(summary = "删除文档", description = "删除指定文档及其所有分块和向量数据")
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(Authentication authentication, @PathVariable String docId) {
        log.info("Deleting document: {}", docId);
        ingestService.deleteDocument(user(authentication), docId);
        return Result.ok(null);
    }

    // ==================== 搜索 ====================

    @Operation(summary = "知识搜索", description = "语义检索知识库，支持 RRF 多路融合，返回最相关的文档分块")
    @GetMapping("/search")
    public Result<KnowledgeRetrievalResult> search(
        Authentication authentication,
        @Parameter(description = "搜索查询文本", required = true)
        @RequestParam @NotBlank String q,
        @Parameter(description = "返回结果数量上限")
        @RequestParam(defaultValue = "5") @Min(1) @Max(50) int topK,
        @Parameter(description = "最小相似度阈值 (0~1)")
        @RequestParam(defaultValue = "0.3") @Min(0) @Max(1) double minScore
    ) {
        log.info("Knowledge search: q='{}', topK={}, minScore={}", q, topK, minScore);
        List<String> docs = ingestService.listDocuments(user(authentication)).stream().map(KnowledgeRepository.KnowledgeDocumentRecord::docId).toList();
        KnowledgeRetrievalResult result = retrievalService.retrieveForUser(user(authentication), docs, q, topK, minScore);
        return Result.ok(result);
    }
    private static long user(Authentication authentication) { return ((AuthenticatedUser) authentication.getPrincipal()).userId(); }
}
