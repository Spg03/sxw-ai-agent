package com.sxw.sxwaiagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * MemorySelector 单元测试
 * <p>
 * 覆盖记忆筛选逻辑：关键词匹配、topN 限制、空列表处理。
 */
@ExtendWith(MockitoExtension.class)
class MemorySelectorTest {

    @Mock
    private MemoryIndex memoryIndex;

    private MemorySelector selector;

    @BeforeEach
    void setUp() {
        selector = new MemorySelector(memoryIndex);
    }

    @Test
    @DisplayName("无活跃记忆：返回空列表")
    void selectRelevant_noMemories_returnsEmpty() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of());

        List<MemoryItem> result = selector.selectRelevant("如何谈恋爱");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("关键词匹配：选中相关记忆")
    void selectRelevant_matchesByKeyword() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of(
                buildMemory("mem-1", "恋爱建议", "用户需要恋爱方面的建议"),
                buildMemory("mem-2", "编程偏好", "用户喜欢 Java 编程"),
                buildMemory("mem-3", "恋爱经历", "用户有过一段恋爱经历")
        ));

        List<MemoryItem> result = selector.selectRelevant("恋爱 建议 如何");

        // 应该匹配到包含"恋爱"的记忆
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(m -> m.name().contains("恋爱")));
    }

    @Test
    @DisplayName("topN 限制：最多返回指定数量")
    void selectRelevant_respectsTopN() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of(
                buildMemory("mem-1", "测试记忆A", "测试相关内容A"),
                buildMemory("mem-2", "测试记忆B", "测试相关内容B"),
                buildMemory("mem-3", "测试记忆C", "测试相关内容C"),
                buildMemory("mem-4", "测试记忆D", "测试相关内容D"),
                buildMemory("mem-5", "测试记忆E", "测试相关内容E"),
                buildMemory("mem-6", "测试记忆F", "测试相关内容F")
        ));

        List<MemoryItem> result = selector.selectRelevant("测试 记忆 内容", 3);

        assertTrue(result.size() <= 3);
    }

    @Test
    @DisplayName("默认 topN=5")
    void selectRelevant_defaultTopN() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of(
                buildMemory("mem-1", "数据A", "数据内容"),
                buildMemory("mem-2", "数据B", "数据内容"),
                buildMemory("mem-3", "数据C", "数据内容"),
                buildMemory("mem-4", "数据D", "数据内容"),
                buildMemory("mem-5", "数据E", "数据内容"),
                buildMemory("mem-6", "数据F", "数据内容"),
                buildMemory("mem-7", "数据G", "数据内容")
        ));

        List<MemoryItem> result = selector.selectRelevant("数据 内容");

        assertTrue(result.size() <= 5);
    }

    @Test
    @DisplayName("空问题：不匹配任何记忆")
    void selectRelevant_blankQuestion_returnsEmpty() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of(
                buildMemory("mem-1", "恋爱建议", "用户需要恋爱方面的建议")
        ));

        List<MemoryItem> result = selector.selectRelevant("");
        assertTrue(result.isEmpty());

        result = selector.selectRelevant("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("英文关键词匹配")
    void selectRelevant_englishKeywords() {
        when(memoryIndex.getActiveMemories()).thenReturn(List.of(
                buildMemory("mem-1", "Java preference", "User prefers Java programming"),
                buildMemory("mem-2", "Python skill", "User knows Python")
        ));

        List<MemoryItem> result = selector.selectRelevant("Java programming tips");

        assertFalse(result.isEmpty());
        assertEquals("mem-1", result.getFirst().memoryId());
    }

    // ───────────────────── buildDetailText ─────────────────────

    @Test
    @DisplayName("buildDetailText：空列表返回空字符串")
    void buildDetailText_emptyList_returnsEmpty() {
        assertEquals("", selector.buildDetailText(null));
        assertEquals("", selector.buildDetailText(List.of()));
    }

    @Test
    @DisplayName("buildDetailText：包含记忆详情")
    void buildDetailText_withMemories_containsDetails() {
        List<MemoryItem> memories = List.of(
                buildMemory("mem-1", "恋爱建议", "用户需要恋爱方面的建议")
        );

        String result = selector.buildDetailText(memories);

        assertTrue(result.contains("## Selected Memory Details"));
        assertTrue(result.contains("恋爱建议"));
        assertTrue(result.contains("**Rule**"));
    }

    // ───────────────────── helpers ─────────────────────

    private MemoryItem buildMemory(String memoryId, String name, String description) {
        return new MemoryItem(
                null, memoryId, MemoryType.USER,
                name, description,
                description, "因为用户提到过", "在相关话题中应用",
                "trace-001", new BigDecimal("0.90"),
                MemoryStatus.ACTIVE,
                LocalDateTime.now(), null, null, null
        );
    }
}
