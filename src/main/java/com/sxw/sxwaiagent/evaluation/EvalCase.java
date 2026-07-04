package com.sxw.sxwaiagent.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测用例
 * <p>
 * 定义一个评测场景，包含输入、期望输出和验证规则。
 * 用于评估 Agent 的能力和质量。
 */
public record EvalCase(
    Long id,
    String caseId,
    String caseName,
    EvalCaseType caseType,
    EvalCaseStatus status,
    String profileCode,
    String inputPrompt,
    String expectedOutput,
    String validationRules,
    List<String> tags,
    Integer priority,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 便捷构造函数
     */
    public EvalCase(
        String caseId,
        String caseName,
        EvalCaseType caseType,
        String profileCode,
        String inputPrompt,
        String expectedOutput
    ) {
        this(null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
            profileCode, inputPrompt, expectedOutput, null, null, 5,
            null, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 检查是否可以运行
     */
    public boolean canRun() {
        return status == EvalCaseStatus.ACTIVE;
    }

    /**
     * 检查是否可以编辑
     */
    public boolean canEdit() {
        return status == EvalCaseStatus.DRAFT || status == EvalCaseStatus.PENDING;
    }

    /**
     * 构建摘要文本
     */
    public String buildSummary() {
        return String.format("[%s] %s (%s) - priority=%d, status=%s",
            caseId, caseName, caseType, priority, status);
    }
}
