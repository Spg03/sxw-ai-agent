package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本向量化服务
 * 
 * 使用 Spring AI 的 EmbeddingModel 将文本转换为向量表示。
 * 支持批量处理和单条处理。
 */
@Service
public class EmbeddingService {
    
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * 单条文本向量化
     */
    public float[] embed(String text) {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getOutput();
            }
            
            log.warn("Empty embedding response for text length: {}", text.length());
            return new float[0];
        } catch (Exception e) {
            log.error("Failed to embed text: {}", e.getMessage());
            return new float[0];
        }
    }
    
    /**
     * 批量文本向量化
     */
    public List<float[]> embedBatch(List<String> texts) {
        try {
            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            if (response != null && response.getResults() != null) {
                return response.getResults().stream()
                    .map(result -> result.getOutput())
                    .toList();
            }
            
            log.warn("Empty embedding response for {} texts", texts.size());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to embed batch: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 获取向量维度
     */
    public int getDimensions() {
        float[] sample = embed("test");
        return sample.length;
    }
}
