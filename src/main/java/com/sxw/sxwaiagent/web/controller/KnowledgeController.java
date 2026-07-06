package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.knowledge.DocumentIngestService;
import com.sxw.sxwaiagent.knowledge.KnowledgeRepository;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 知识库管理 API
 * 
 * 提供文档入库、查询、删除等 REST 接口。
 */
@RestController
@RequestMapping("/api/knowledge")
@Validated
public class KnowledgeController {
    
    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    
    private final DocumentIngestService ingestService;
    
    public KnowledgeController(DocumentIngestService ingestService) {
        this.ingestService = ingestService;
    }
    
    /**
     * 上传并入库文档
     */
    @PostMapping("/documents")
    public Result<DocumentIngestService.IngestResult> uploadDocument(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("Uploading document: {}", file.getOriginalFilename());
        
        // 保存临时文件
        Path tempFile = Files.createTempFile("knowledge_", "_" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);
            
            DocumentIngestService.IngestResult result = ingestService.ingestFromFile(tempFile);
            
            if (result.isSuccess()) {
                return Result.ok(result);
            } else {
                return Result.error(result.message());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    
    /**
     * 从文本内容入库
     */
    @PostMapping("/documents/text")
    public Result<DocumentIngestService.IngestResult> ingestText(
        @RequestParam @NotBlank String title,
        @RequestParam(required = false) String sourcePath,
        @RequestBody @NotBlank String content
    ) {
        log.info("Ingesting text document: {}", title);
        
        DocumentIngestService.IngestResult result = ingestService.ingest(
            title,
            sourcePath != null ? sourcePath : "text-input",
            content
        );
        
        if (result.isSuccess()) {
            return Result.ok(result);
        } else {
            return Result.error(result.message());
        }
    }
    
    /**
     * 列出所有文档
     */
    @GetMapping("/documents")
    public Result<List<KnowledgeRepository.KnowledgeDocumentRecord>> listDocuments() {
        List<KnowledgeRepository.KnowledgeDocumentRecord> docs = ingestService.listDocuments();
        return Result.ok(docs);
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable String docId) {
        log.info("Deleting document: {}", docId);
        ingestService.deleteDocument(docId);
        return Result.ok(null);
    }
}
