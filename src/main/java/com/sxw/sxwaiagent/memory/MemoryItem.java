package com.sxw.sxwaiagent.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记忆项
 * <p>
 * 结构化记忆的基本单元，包含 Index（摘要）和 Detail（详情）两部分。
 * Index 常驻 Prompt，Detail 按需加载。
 */
public record MemoryItem(
    Long id,
    String memoryId,
    MemoryType memoryType,
    String name,
    String description,
    String ruleText,
    String whyText,
    String applyText,
    String sourceTraceId,
    BigDecimal confidence,
    MemoryStatus status,
    LocalDateTime createdAt,
    LocalDateTime expiredAt,
    LocalDateTime reviewedAt,
    String reviewedBy
) {
    /**
     * 构建 Index 摘要（用于 Prompt 常驻）
     */
    public String buildIndexSummary() {
        return String.format("[%s] %s: %s (confidence=%.2f)", 
            memoryId, name, description, confidence);
    }
    
    /**
     * 构建 Detail 内容（按需加载）
     */
    public String buildDetailContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Memory: ").append(name).append("\n\n");
        
        if (ruleText != null && !ruleText.isBlank()) {
            sb.append("**Rule**: ").append(ruleText).append("\n\n");
        }
        
        if (whyText != null && !whyText.isBlank()) {
            sb.append("**Why**: ").append(whyText).append("\n\n");
        }
        
        if (applyText != null && !applyText.isBlank()) {
            sb.append("**Apply**: ").append(applyText).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 检查记忆是否有效
     */
    public boolean isActive() {
        if (status != MemoryStatus.ACTIVE) {
            return false;
        }
        if (expiredAt != null && expiredAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }
}
