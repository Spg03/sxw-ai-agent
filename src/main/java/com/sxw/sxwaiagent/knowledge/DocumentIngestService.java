package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * 文档入库服务
 * 
 * 编排文档入库的完整流程：
 * 1. 读取文件内容
 * 2. 分割为语义块
 * 3. 生成向量嵌入
 * 4. 持久化到数据库
 */
@Service
public class DocumentIngestService {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);
    
    private final MarkdownTextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final KnowledgeRepository knowledgeRepository;
    
    public DocumentIngestService(
        MarkdownTextSplitter textSplitter,
        EmbeddingService embeddingService,
        KnowledgeRepository knowledgeRepository
    ) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.knowledgeRepository = knowledgeRepository;
    }
    
    /**
     * 从文件路径入库
     */
    public IngestResult ingestFromFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String title = filePath.getFileName().toString();
        
        return ingest(title, filePath.toString(), content);
    }
    
    /**
     * 从文本内容入库
     */
    public IngestResult ingest(String title, String sourcePath, String content) {
        log.info("Ingesting document: {} from {}", title, sourcePath);

        // 0. 增量索引：计算 content hash，检查是否已存在
        String contentHash = computeSha256(content);
        Optional<KnowledgeRepository.KnowledgeDocumentRecord> existing =
            knowledgeRepository.findByContentHash(contentHash);

        if (existing.isPresent()) {
            KnowledgeRepository.KnowledgeDocumentRecord doc = existing.get();
            log.info("Document already indexed, skipping: {} (docId={})", title, doc.docId());
            // 刷新 updated_at 以表明本次访问仍然活跃
            knowledgeRepository.updateDocument(doc.docId());
            return new IngestResult(doc.docId(), doc.chunkCount(), "Already indexed", IngestStatus.SKIPPED);
        }

        // 1. 分割文档
        List<MarkdownTextSplitter.DocumentChunk> chunks = textSplitter.split(content, "temp_" + System.currentTimeMillis());
        
        if (chunks.isEmpty()) {
            log.warn("No chunks generated from document: {}", title);
            return new IngestResult(null, 0, "No content to index", null);
        }
        
        // 2. 生成嵌入向量
        List<String> chunkTexts = chunks.stream()
            .map(c -> c.breadcrumb() + "\n" + c.content())
            .toList();
        
        List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);
        
        if (embeddings.isEmpty()) {
            log.error("Failed to generate embeddings for document: {}", title);
            return new IngestResult(null, 0, "Embedding generation failed", null);
        }
        
        // 3. 保存文档元数据（含 content_hash）
        String docId = knowledgeRepository.saveDocument(title, sourcePath, chunks.size(), contentHash);
        
        // 4. 更新 chunk 的 docId 并保存
        List<MarkdownTextSplitter.DocumentChunk> updatedChunks = chunks.stream()
            .map(c -> new MarkdownTextSplitter.DocumentChunk(
                c.chunkId().replace("temp_", docId + "_"),
                docId,
                c.chunkIndex(),
                c.breadcrumb(),
                c.content(),
                c.tokenCount()
            ))
            .toList();
        
        knowledgeRepository.saveChunks(updatedChunks, embeddings);
        
        log.info("Successfully ingested document {} with {} chunks", docId, chunks.size());
        return new IngestResult(docId, chunks.size(), "Success", IngestStatus.CREATED);
    }
    
    /**
     * 删除文档
     */
    public void deleteDocument(String docId) {
        knowledgeRepository.deleteDocument(docId);
        log.info("Deleted document: {}", docId);
    }
    
    /**
     * 列出所有文档
     */
    public List<KnowledgeRepository.KnowledgeDocumentRecord> listDocuments() {
        return knowledgeRepository.listDocuments();
    }
    
    /**
     * 计算内容的 SHA-256 摘要（十六进制字符串）
     */
    private String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在所有 JVM 实现中均受支持，不会抛此异常
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IngestResult(
        String docId,
        int chunkCount,
        String message,
        IngestStatus status
    ) {
        public boolean isSuccess() {
            return docId != null;
        }
    }
}
