package com.sxw.sxwaiagent.evaluation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A single evaluation case defining input, expected output, and validation rules.
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
    String judgeCriteria,
    ValidationMode validationMode,
    List<String> tags,
    Integer priority,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * Convenience constructor with defaults for new cases.
     */
    public EvalCase(
        String caseId, String caseName, EvalCaseType caseType,
        String profileCode, String inputPrompt, String expectedOutput
    ) {
        this(null, caseId, caseName, caseType, EvalCaseStatus.DRAFT,
            profileCode, inputPrompt, expectedOutput, null,
            null, ValidationMode.KEYWORD_ONLY,
            null, 5, null, LocalDateTime.now(), LocalDateTime.now());
    }

    public boolean canRun() {
        return status == EvalCaseStatus.ACTIVE;
    }

    public boolean canEdit() {
        return status == EvalCaseStatus.DRAFT || status == EvalCaseStatus.PENDING;
    }

    public String buildSummary() {
        return String.format("[%s] %s (%s) - priority=%d, status=%s",
            caseId, caseName, caseType, priority, status);
    }
}
