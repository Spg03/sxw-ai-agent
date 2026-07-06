package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        
        // 1. 分割文档
        List<MarkdownTextSplitter.DocumentChunk> chunks = textSplitter.split(content, "temp_" + System.currentTimeMillis());
        
        if (chunks.isEmpty()) {
            log.warn("No chunks generated from document: {}", title);
            return new IngestResult(null, 0, "No content to index");
        }
        
        // 2. 生成嵌入向量
        List<String> chunkTexts = chunks.stream()
            .map(c -> c.breadcrumb() + "\n" + c.content())
            .toList();
        
        List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);
        
        if (embeddings.isEmpty()) {
            log.error("Failed to generate embeddings for document: {}", title);
            return new IngestResult(null, 0, "Embedding generation failed");
        }
        
        // 3. 保存文档元数据
        String docId = knowledgeRepository.saveDocument(title, sourcePath, chunks.size());
        
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
        return new IngestResult(docId, chunks.size(), "Success");
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
    
    public record IngestResult(
        String docId,
        int chunkCount,
        String message
    ) {
        public boolean isSuccess() {
            return docId != null;
        }
    }
}
