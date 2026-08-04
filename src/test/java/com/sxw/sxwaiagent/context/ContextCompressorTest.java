package com.sxw.sxwaiagent.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextCompressor + TokenCounter 单元测试
 * <p>
 * 覆盖上下文压缩策略和 Token 估算逻辑。
 */
class ContextCompressorTest {

    private final ContextCompressor compressor = new ContextCompressor();
    private final TokenCounter tokenCounter = new TokenCounter();

    // ───────────────────── TokenCounter ─────────────────────

    @Nested
    @DisplayName("TokenCounter 测试")
    class TokenCounterTests {

        @Test
        @DisplayName("空文本返回 0")
        void count_emptyText_returnsZero() {
            assertEquals(0, tokenCounter.count(null));
            assertEquals(0, tokenCounter.count(""));
        }

        @Test
        @DisplayName("纯英文文本：约 4 字符/token")
        void count_englishText() {
            // "hello world" = 11 chars → ceil(11/4) = 3 tokens
            int tokens = tokenCounter.count("hello world");
            assertEquals(3, tokens);
        }

        @Test
        @DisplayName("纯中文文本：每字约 1.5 token")
        void count_chineseText() {
            // "你好世界" = 4 CJK chars → ceil(4 * 1.5) = 6 tokens
            int tokens = tokenCounter.count("你好世界");
            assertEquals(6, tokens);
        }

        @Test
        @DisplayName("中英混合文本")
        void count_mixedText() {
            // "hello你好" = 5 non-CJK + 2 CJK → ceil(5/4 + 2*1.5) = ceil(1.25+3) = 5
            int tokens = tokenCounter.count("hello你好");
            assertEquals(5, tokens);
        }

        @Test
        @DisplayName("truncate：未超限不截断")
        void truncate_withinBudget_noChange() {
            String text = "short text";
            assertEquals(text, tokenCounter.truncate(text, 100));
        }

        @Test
        @DisplayName("truncate：超限截断并附加提示")
        void truncate_overBudget_truncates() {
            String text = "a".repeat(1000);
            String result = tokenCounter.truncate(text, 10);
            assertTrue(result.length() < text.length());
            assertTrue(result.contains("...[truncated"));
        }

        @Test
        @DisplayName("truncate：null 返回 null")
        void truncate_null_returnsNull() {
            assertNull(tokenCounter.truncate(null, 10));
        }
    }

    // ───────────────────── ContextCompressor ─────────────────────

    @Nested
    @DisplayName("compressStatic 测试")
    class CompressStaticTests {

        @Test
        @DisplayName("内容未超限：原样返回")
        void withinBudget_returnsOriginal() {
            String content = "Hello World";
            assertEquals(content, compressor.compressStatic(content, 100));
        }

        @Test
        @DisplayName("内容超限：截断并附加提示")
        void overBudget_truncates() {
            String content = "x".repeat(2000);
            String result = compressor.compressStatic(content, 100); // 100 tokens = 400 chars
            assertTrue(result.length() < content.length());
            assertTrue(result.contains("...[truncated"));
        }

        @Test
        @DisplayName("null 输入返回 null")
        void nullInput_returnsNull() {
            assertNull(compressor.compressStatic(null, 100));
        }
    }

    @Nested
    @DisplayName("compressKnowledge 测试")
    class CompressKnowledgeTests {

        @Test
        @DisplayName("多段落知识：按预算截断")
        void multipleParagraphs_truncatesByBudget() {
            // 使用较长段落确保截断后确实变短
            String para = "这是一段比较长的知识内容，包含了大量的文字信息用于测试压缩功能是否正常工作。";
            String content = para + "\n\n" + para + "\n\n" + para + "\n\n" + para;
            // 给一个很小的预算，只够放 1 个段落
            String result = compressor.compressKnowledge(content, 10);
            assertTrue(result.contains("...[knowledge truncated"));
            assertTrue(result.length() < content.length());
        }

        @Test
        @DisplayName("预算充足：全部保留")
        void sufficientBudget_keepsAll() {
            String content = "短段落。\n\n另一个短段落。";
            String result = compressor.compressKnowledge(content, 1000);
            assertTrue(result.contains("短段落"));
            assertTrue(result.contains("另一个短段落"));
        }
    }

    @Nested
    @DisplayName("compressToolResults 测试")
    class CompressToolResultsTests {

        @Test
        @DisplayName("短结果：不压缩")
        void shortResult_noCompression() {
            String content = "OK: file created";
            String result = compressor.compressToolResults(content, 1000);
            assertEquals(content, result);
        }

        @Test
        @DisplayName("长结果（>500字符）：压缩为预览")
        void longResult_compressesToPreview() {
            String content = "A".repeat(1000);
            String result = compressor.compressToolResults(content, 1000);
            assertTrue(result.contains("...[tool result compressed"));
            assertTrue(result.length() < content.length());
        }
    }

    @Nested
    @DisplayName("compressHistory 测试")
    class CompressHistoryTests {

        @Test
        @DisplayName("空历史：返回空列表")
        void emptyHistory_returnsEmpty() {
            assertTrue(compressor.compressHistory(null, 100, tokenCounter).isEmpty());
            assertTrue(compressor.compressHistory(List.of(), 100, tokenCounter).isEmpty());
        }

        @Test
        @DisplayName("未超限：原样返回")
        void withinBudget_returnsOriginal() {
            List<Message> history = List.of(
                    new UserMessage("你好"),
                    new AssistantMessage("你好！有什么可以帮助你的？")
            );
            List<Message> result = compressor.compressHistory(history, 10000, tokenCounter);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("超限且消息数>10：压缩旧消息为摘要")
        void overBudget_manyMessages_summarizesOld() {
            List<Message> history = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                history.add(new UserMessage("消息 " + i + " 包含一些内容用于测试压缩功能"));
            }
            // 给一个很小的预算迫使压缩
            List<Message> result = compressor.compressHistory(history, 50, tokenCounter);
            assertTrue(result.size() < history.size());
            // 第一条应该是摘要
            assertTrue(result.getFirst().getText().contains("[history summary]"));
        }

        @Test
        @DisplayName("超限且消息数<=10：从最旧开始丢弃")
        void overBudget_fewMessages_dropsOldest() {
            List<Message> history = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                history.add(new UserMessage("这是一条比较长的测试消息编号 " + i + "，用于验证压缩逻辑是否正常工作"));
            }
            // 给一个只够放几条消息的预算
            List<Message> result = compressor.compressHistory(history, 30, tokenCounter);
            assertTrue(result.size() < history.size());
            // 不应该有摘要（因为 <= 10 条）
            assertTrue(result.stream().noneMatch(m -> m.getText().contains("[history summary]")));
        }
    }
}
