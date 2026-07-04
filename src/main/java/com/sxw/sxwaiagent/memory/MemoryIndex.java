package com.sxw.sxwaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆索引
 * <p>
 * 轻量级摘要列表，常驻 Prompt，用于快速筛选相关记忆。
 * 最大 Token 预算：500 tokens（约 20 条记忆）。
 */
@Component
public class MemoryIndex {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryIndex.class);
    private static final int MAX_INDEX_ITEMS = 20;
    
    private final MemoryRepository memoryRepository;
    
    public MemoryIndex(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }
    
    /**
     * 构建 Index 摘要文本（用于注入 Prompt）
     */
    public String buildIndexText() {
        List<MemoryItem> activeMemories = memoryRepository.findAllActive();
        
        if (activeMemories.isEmpty()) {
            return "暂无长期记忆。";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("## Long-term Memory Index\n\n");
        
        int count = 0;
        for (MemoryItem item : activeMemories) {
            if (count >= MAX_INDEX_ITEMS) {
                sb.append("\n... (truncated, total ").append(activeMemories.size()).append(" memories)");
                break;
            }
            sb.append("- ").append(item.buildIndexSummary()).append("\n");
            count++;
        }
        
        return sb.toString();
    }
    
    /**
     * 获取所有活跃记忆（用于 MemorySelector 筛选）
     */
    public List<MemoryItem> getActiveMemories() {
        return memoryRepository.findAllActive();
    }
    
    /**
     * 根据类型获取记忆
     */
    public List<MemoryItem> getMemoriesByType(MemoryType type) {
        return memoryRepository.findByTypeAndStatus(type, MemoryStatus.ACTIVE);
    }
    
    /**
     * 刷新索引（当前实现直接查询数据库，后续可加缓存）
     */
    public void refresh() {
        log.debug("MemoryIndex refreshed, active count: {}", memoryRepository.countActive());
    }
}
