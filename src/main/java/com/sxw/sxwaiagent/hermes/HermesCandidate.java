package com.sxw.sxwaiagent.hermes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hermes 候选记录
 * 
 * Agent 从对话中提取的待审核经验总结
 */
public record HermesCandidate(
    String candidateId,
    String runId,
    String chatId,
    CandidateType type,
    String title,
    String content,
    String metadata,
    CandidateStatus status,
    String reviewedBy,
    Instant createdAt,
    Instant reviewedAt,
    String sourceTraceId,
    BigDecimal confidence
) {
    public enum CandidateStatus {
        PENDING,        // 待审核
        APPROVED,       // 已批准
        REJECTED,       // 已拒绝
        APPLIED,        // 已成功应用
        APPLY_FAILED    // 应用失败，可重试
    }

    /**
     * 别名方法，兼容 candidateType() 调用
     */
    public CandidateType candidateType() {
        return type;
    }

    /**
     * 判断候选是否可以被应用
     */
    public boolean canApply() {
        return status == CandidateStatus.APPROVED;
    }
}
