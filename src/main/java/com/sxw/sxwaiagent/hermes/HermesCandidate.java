package com.sxw.sxwaiagent.hermes;

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
    Instant reviewedAt
) {
    public enum CandidateStatus {
        PENDING,    // 待审核
        APPROVED,   // 已批准
        REJECTED    // 已拒绝
    }
}
