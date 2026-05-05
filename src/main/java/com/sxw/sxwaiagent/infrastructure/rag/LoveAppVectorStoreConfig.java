package com.sxw.sxwaiagent.infrastructure.rag;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 恋爱大师向量数据库配置（基于内存）。
 * 启动期任一步骤失败（key 无效 / 网络异常）→ 降级返回空 SimpleVectorStore，
 * 保证应用上下文一定能起来；运行期 RAG 检索为空，但聊天 / 工具调用不受影响。
 */
@Configuration
public class LoveAppVectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(LoveAppVectorStoreConfig.class);

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        try {
            List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
            simpleVectorStore.add(enrichedDocuments);
            log.info("LoveApp vector store initialized with {} documents", enrichedDocuments.size());
        } catch (Exception e) {
            log.warn("Failed to initialize LoveApp vector store ({}). " +
                    "Returning empty store; RAG retrieval will be disabled until DASHSCOPE_API_KEY is fixed.",
                    e.getMessage());
        }
        return simpleVectorStore;
    }
}
