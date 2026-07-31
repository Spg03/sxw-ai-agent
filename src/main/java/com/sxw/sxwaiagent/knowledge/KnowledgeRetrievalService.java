package com.sxw.sxwaiagent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识检索服务
 * 统一的检索入口，支持多后端策略和 RRF 融合
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    private final List<KnowledgeRetriever> retrievers;

    @Value("${sxw.knowledge.rrf.enabled:true}")
    private boolean rrfEnabled;

    @Value("${sxw.knowledge.rrf.k:60}")
    private int rrfK;

    public KnowledgeRetrievalService(List<KnowledgeRetriever> retrievers) {
        this.retrievers = retrievers;
        log.info("Initialized KnowledgeRetrievalService with {} retrievers: {}",
            retrievers.size(),
            retrievers.stream().map(KnowledgeRetriever::getName).toList());
    }

    /**
     * 使用所有可用的检索器检索知识（根据配置自动选择 RRF 或简单拼接）
     *
     * @param query    查询文本
     * @param topK     每个检索器返回的最大结果数
     * @param minScore 最小相似度阈值
     * @return 合并后的检索结果
     */
    public KnowledgeRetrievalResult retrieveFromAll(String query, int topK, double minScore) {
        if (rrfEnabled) {
            return retrieveWithRRF(query, topK, minScore);
        }
        return retrieveSimple(query, topK, minScore);
    }

    /**
     * 简单拼接检索（原逻辑）：合并所有 retriever 结果，按原始 score 降序取 topK
     */
    private KnowledgeRetrievalResult retrieveSimple(String query, int topK, double minScore) {
        List<KnowledgeChunk> allChunks = retrievers.stream()
            .filter(KnowledgeRetriever::isAvailable)
            .flatMap(retriever -> retriever.retrieve(query, topK, minScore).chunks().stream())
            .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
            .limit(topK)
            .toList();

        return new KnowledgeRetrievalResult(allChunks, allChunks.size(), query, 0L);
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合检索
     * <p>
     * 1. 对每个可用 retriever 独立检索，获取排序后的结果列表
     * 2. 对每个 retriever 的结果，按排名计算 RRF score: score = sum(1.0 / (k + rank_i))
     * 3. 合并所有 retriever 的 RRF score（同一 chunk 的 score 累加）
     * 4. 按合并后的 RRF score 降序排序，取 topK
     *
     * @param query    查询文本
     * @param topK     最终返回的最大结果数
     * @param minScore 最小相似度阈值（用于单个 retriever 预过滤）
     * @return RRF 融合后的检索结果
     */
    public KnowledgeRetrievalResult retrieveWithRRF(String query, int topK, double minScore) {
        long startTime = System.currentTimeMillis();

        // chunkId -> 累积 RRF score
        Map<String, Double> rrfScores = new HashMap<>();
        // chunkId -> KnowledgeChunk（保留第一条用于返回）
        Map<String, KnowledgeChunk> chunkMap = new LinkedHashMap<>();

        List<KnowledgeRetriever> available = retrievers.stream()
            .filter(KnowledgeRetriever::isAvailable)
            .toList();

        for (KnowledgeRetriever retriever : available) {
            KnowledgeRetrievalResult result;
            try {
                result = retriever.retrieve(query, topK, minScore);
            } catch (RuntimeException e) {
                log.warn("Retriever '{}' failed, skipping: {}", retriever.getName(), e.getMessage());
                continue;
            }

            List<KnowledgeChunk> chunks = result.chunks();
            for (int rank = 0; rank < chunks.size(); rank++) {
                KnowledgeChunk chunk = chunks.get(rank);
                double rrfScore = 1.0 / (rrfK + rank + 1); // rank 从 0 开始，加 1 对齐公式
                rrfScores.merge(chunk.chunkId(), rrfScore, Double::sum);
                chunkMap.putIfAbsent(chunk.chunkId(), chunk);
            }
        }

        // 按 RRF score 降序排序，取 topK
        List<KnowledgeChunk> fused = rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> {
                KnowledgeChunk original = chunkMap.get(entry.getKey());
                // 用 RRF score 替换原始 similarity，便于下游感知融合权重
                return new KnowledgeChunk(
                    original.chunkId(),
                    original.content(),
                    entry.getValue(),
                    original.source(),
                    original.documentId(),
                    original.documentName()
                );
            })
            .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("RRF fusion completed: {} retrievers, {} results in {}ms",
            available.size(), fused.size(), elapsed);

        return new KnowledgeRetrievalResult(fused, fused.size(), query, elapsed);
    }

    /**
     * 使用指定检索器检索知识
     *
     * @param retrieverName 检索器名称
     * @param query         查询文本
     * @param topK          返回的最大结果数
     * @param minScore      最小相似度阈值
     * @return 检索结果
     */
    public KnowledgeRetrievalResult retrieveFrom(String retrieverName, String query, int topK, double minScore) {
        return findRetriever(retrieverName)
            .map(retriever -> retriever.retrieve(query, topK, minScore))
            .orElseGet(() -> {
                log.warn("Retriever '{}' not found or not available", retrieverName);
                return KnowledgeRetrievalResult.empty(query);
            });
    }

    /**
     * 使用所有可用的检索器检索知识（默认参数）
     *
     * @param query 查询文本
     * @return 检索结果
     */
    public KnowledgeRetrievalResult retrieve(String query) {
        return retrieveFromAll(query, 5, 0.5);
    }

    /**
     * 获取所有可用的检索器
     *
     * @return 可用的检索器列表
     */
    public List<KnowledgeRetriever> getAvailableRetrievers() {
        return retrievers.stream()
            .filter(KnowledgeRetriever::isAvailable)
            .toList();
    }

    /**
     * 检查是否有任何检索器可用
     *
     * @return 是否有可用检索器
     */
    public boolean hasAvailableRetriever() {
        return retrievers.stream().anyMatch(KnowledgeRetriever::isAvailable);
    }

    private Optional<KnowledgeRetriever> findRetriever(String name) {
        return retrievers.stream()
            .filter(r -> r.getName().equals(name))
            .filter(KnowledgeRetriever::isAvailable)
            .findFirst();
    }
}
