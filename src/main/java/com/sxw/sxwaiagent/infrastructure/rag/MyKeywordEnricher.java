package com.sxw.sxwaiagent.infrastructure.rag;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 * 启动期若 LLM 不可用（无 key / 401 / 网络异常），降级返回原文档，避免阻塞容器启动。
 */
@Component
public class MyKeywordEnricher {

    private static final Logger log = LoggerFactory.getLogger(MyKeywordEnricher.class);

    @Resource
    private ChatModel dashscopeChatModel;

    @Value("${sxw.rag.keyword-enrich.enabled:true}")
    private boolean enabled;

    public List<Document> enrichDocuments(List<Document> documents) {
        if (!enabled) {
            log.info("Keyword enrichment disabled by config, skip {} docs", documents.size());
            return documents;
        }
        try {
            KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);
            return enricher.apply(documents);
        } catch (Exception e) {
            log.warn("Keyword enrichment failed ({}), fallback to raw documents. Check DASHSCOPE_API_KEY.",
                    e.getMessage());
            return documents;
        }
    }
}
