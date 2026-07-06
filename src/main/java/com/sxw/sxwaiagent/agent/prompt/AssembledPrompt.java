package com.sxw.sxwaiagent.agent.prompt;

import java.util.List;

/**
 * 组装后的 Prompt 结果
 * 
 * @param rendered 最终渲染的完整 Prompt 文本
 * @param sections 所有 Section 列表
 * @param staticHash 静态部分的 SHA-256 哈希
 * @param dynamicHash 动态部分的 SHA-256 哈希
 * @param renderedHash 完整 Prompt 的 SHA-256 哈希
 */
public record AssembledPrompt(
    String rendered,
    List<PromptSection> sections,
    String staticHash,
    String dynamicHash,
    String renderedHash
) {
    public int renderedLength() {
        return rendered != null ? rendered.length() : 0;
    }
    
    public long staticSectionCount() {
        return sections.stream().filter(PromptSection::isStatic).count();
    }
    
    public long dynamicSectionCount() {
        return sections.stream().filter(PromptSection::isDynamic).count();
    }
}
