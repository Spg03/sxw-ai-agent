package com.sxw.sxwaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆选择器
 * <p>
 * 根据用户问题筛选相关记忆，返回 topN MemoryDetail。
 * 选择策略：关键词匹配 + 类型匹配（后续可升级为语义匹配）。
 */
@Component
public class MemorySelector {
    
    private static final Logger log = LoggerFactory.getLogger(MemorySelector.class);
    private static final int DEFAULT_TOP_N = 5;
    
    private final MemoryIndex memoryIndex;
    
    public MemorySelector(MemoryIndex memoryIndex) {
        this.memoryIndex = memoryIndex;
    }
    
    /**
     * 选择相关记忆
     * 
     * @param userQuestion 用户问题
     * @param topN 返回的最大数量
     * @return 相关记忆列表
     */
    public List<MemoryItem> selectRelevant(String userQuestion, int topN) {
        List<MemoryItem> allMemories = memoryIndex.getActiveMemories();
        
        if (allMemories.isEmpty()) {
            return List.of();
        }
        
        // 简单策略：关键词匹配 + 类型优先级
        List<MemoryItem> selected = allMemories.stream()
            .filter(item -> isRelevant(item, userQuestion))
            .limit(topN)
            .collect(Collectors.toList());
        
        log.debug("Selected {} memories for question: {}", selected.size(), 
            userQuestion.length() > 50 ? userQuestion.substring(0, 50) + "..." : userQuestion);
        
        return selected;
    }
    
    /**
     * 选择相关记忆（默认 topN=5）
     */
    public List<MemoryItem> selectRelevant(String userQuestion) {
        return selectRelevant(userQuestion, DEFAULT_TOP_N);
    }
    
    /**
     * 构建 Detail 内容（用于注入 Prompt）
     */
    public String buildDetailText(List<MemoryItem> selectedMemories) {
        if (selectedMemories == null || selectedMemories.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n## Selected Memory Details\n\n");
        
        for (MemoryItem item : selectedMemories) {
            sb.append(item.buildDetailContent());
            sb.append("\n---\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 判断记忆是否与问题相关
     * <p>
     * 当前实现：简单关键词匹配
     * 后续升级：语义相似度匹配（需要 Embedding 服务）
     */
    private boolean isRelevant(MemoryItem item, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        // 检查 name 和 description 是否包含问题中的关键词
        String nameLower = item.name().toLowerCase();
        String descLower = item.description().toLowerCase();
        
        // 简单启发式：如果 name 或 description 中包含问题的前 3 个词，认为相关
        String[] words = lowerQuestion.split("\\s+");
        int matchCount = 0;
        for (int i = 0; i < Math.min(3, words.length); i++) {
            String word = words[i].replaceAll("[^\\w\\u4e00-\\u9fa5]", ""); // 移除标点
            if (word.length() > 1 && (nameLower.contains(word) || descLower.contains(word))) {
                matchCount++;
            }
        }
        
        // 至少匹配 1 个词认为相关
        return matchCount > 0;
    }
}
