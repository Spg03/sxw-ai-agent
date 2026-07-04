package com.sxw.sxwaiagent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 记忆服务
 * <p>
 * 统一入口，提供记忆的查询、选择、管理能力。
 * 注意：记忆的写入必须通过 Hermes 审核流程，不能直接调用。
 */
@Service
public class MemoryService {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    
    private final MemoryRepository memoryRepository;
    private final MemoryIndex memoryIndex;
    private final MemorySelector memorySelector;
    
    public MemoryService(
        MemoryRepository memoryRepository,
        MemoryIndex memoryIndex,
        MemorySelector memorySelector
    ) {
        this.memoryRepository = memoryRepository;
        this.memoryIndex = memoryIndex;
        this.memorySelector = memorySelector;
    }
    
    /**
     * 获取 Index 摘要文本（用于 Prompt 常驻）
     */
    public String getIndexText() {
        return memoryIndex.buildIndexText();
    }
    
    /**
     * 选择相关记忆并构建 Detail 文本（用于 Prompt 动态注入）
     */
    public String getRelevantDetailText(String userQuestion) {
        List<MemoryItem> selected = memorySelector.selectRelevant(userQuestion);
        return memorySelector.buildDetailText(selected);
    }
    
    /**
     * 选择相关记忆
     */
    public List<MemoryItem> selectRelevant(String userQuestion, int topN) {
        return memorySelector.selectRelevant(userQuestion, topN);
    }
    
    /**
     * 根据 memoryId 查找
     */
    public Optional<MemoryItem> findByMemoryId(String memoryId) {
        return memoryRepository.findByMemoryId(memoryId);
    }
    
    /**
     * 审核通过（由 Hermes 审核流程调用）
     */
    public void approve(String memoryId, String reviewedBy) {
        memoryRepository.updateStatus(memoryId, MemoryStatus.ACTIVE, reviewedBy);
        memoryIndex.refresh();
        log.info("Memory approved: {} by {}", memoryId, reviewedBy);
    }
    
    /**
     * 审核拒绝（由 Hermes 审核流程调用）
     */
    public void reject(String memoryId, String reviewedBy) {
        memoryRepository.updateStatus(memoryId, MemoryStatus.ARCHIVED, reviewedBy);
        log.info("Memory rejected: {} by {}", memoryId, reviewedBy);
    }
    
    /**
     * 归档记忆
     */
    public void archive(String memoryId, String reviewedBy) {
        memoryRepository.updateStatus(memoryId, MemoryStatus.ARCHIVED, reviewedBy);
        memoryIndex.refresh();
        log.info("Memory archived: {} by {}", memoryId, reviewedBy);
    }
    
    /**
     * 删除记忆
     */
    public void delete(String memoryId) {
        memoryRepository.delete(memoryId);
        memoryIndex.refresh();
        log.info("Memory deleted: {}", memoryId);
    }
    
    /**
     * 统计活跃记忆数量
     */
    public long countActive() {
        return memoryRepository.countActive();
    }
    
    /**
     * 获取所有活跃记忆
     */
    public List<MemoryItem> getAllActive() {
        return memoryIndex.getActiveMemories();
    }
}
