package com.sxw.sxwaiagent.agent.prompt;

/**
 * Prompt 分区定义
 * 
 * 每个 Section 代表 Prompt 中的一个逻辑部分，可以是静态的或动态的。
 * 
 * @param key 分区标识（如 ROLE、SAFETY、MEMORY_INDEX）
 * @param type 分区类型（STATIC 或 DYNAMIC）
 * @param content 分区内容
 * @param order 排序权重（用于组装时的排序）
 */
public record PromptSection(
    String key,
    SectionType type,
    String content,
    int order
) {
    public enum SectionType {
        STATIC,
        DYNAMIC
    }
    
    public static PromptSection staticSection(String key, String content, int order) {
        return new PromptSection(key, SectionType.STATIC, content, order);
    }
    
    public static PromptSection dynamicSection(String key, String content, int order) {
        return new PromptSection(key, SectionType.DYNAMIC, content, order);
    }
    
    public boolean isStatic() {
        return type == SectionType.STATIC;
    }
    
    public boolean isDynamic() {
        return type == SectionType.DYNAMIC;
    }
}
