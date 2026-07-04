package com.sxw.sxwaiagent.hermes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hermes 候选项
 * <p>
 * Hermes 分析器生成的改进候选，需要用户审核后才能应用。
 * 这是 Hermes 复盘系统的核心数据模型。
 */
public record HermesCandidate(
    Long id,
    String candidateId,
    String sourceRequestId,
    String sourceTraceId,
    HermesCandidateType candidateType,
    String title,
    String content,
    String targetStore,
    BigDecimal confidence,
    HermesCandidateStatus status,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt,
    String reviewedBy,
    LocalDateTime appliedAt,
    String applyResult
) {
    /**
     * 便捷构造函数：创建 PENDING 状态的候选
     */
    public HermesCandidate(
        String candidateId,
        String sourceRequestId,
        String sourceTraceId,
        HermesCandidateType candidateType,
        String title,
        String content,
        String targetStore,
        BigDecimal confidence
    ) {
        this(null, candidateId, sourceRequestId, sourceTraceId, candidateType,
            title, content, targetStore, confidence,
            HermesCandidateStatus.PENDING, LocalDateTime.now(),
            null, null, null, null);
    }

    /**
     * 检查是否可以应用
     */
    public boolean canApply() {
        return status == HermesCandidateStatus.APPROVED;
    }

    /**
     * 检查是否可以审核
     */
    public boolean canReview() {
        return status == HermesCandidateStatus.PENDING;
    }

    /**
     * 检查是否高置信度（可自动批准）
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence.compareTo(new BigDecimal("0.9")) >= 0;
    }

    /**
     * 构建摘要文本
     */
    public String buildSummary() {
        return String.format("[%s] %s (%s) - confidence=%.2f, status=%s",
            candidateId, title, candidateType,
            confidence != null ? confidence : BigDecimal.ZERO,
            status);
    }
}
