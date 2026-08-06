package com.sxw.sxwaiagent.agent.runtime;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptAssembler;
import com.sxw.sxwaiagent.agent.prompt.PromptRunRecorder;
import com.sxw.sxwaiagent.agent.tool.ToolExecutor;
import com.sxw.sxwaiagent.agent.tool.ToolRegistry;
import com.sxw.sxwaiagent.agent.tool.ToolResult;
import com.sxw.sxwaiagent.plan.PlanReviewService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ToolUseLoopRuntime 聚焦测试
 * <p>
 * 覆盖核心循环的边界决策：
 * <ul>
 *   <li>模型直接返回最终答案（end_turn 路径）</li>
 *   <li>模型返回 tool_use → 执行工具 → 继续循环 → 最终答案</li>
 *   <li>工具不在 Profile 允许列表中 → 拒绝执行</li>
 *   <li>LLM 调用失败 → 降级 fallback 响应</li>
 *   <li>达到最大轮次限制</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ToolUseLoopRuntimeTest {

    @Mock private ChatModel chatModel;
    @Mock private ToolExecutor toolExecutor;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ChatMemory chatMemory;
    @Mock private PromptAssembler promptAssembler;
    @Mock private PromptRunRecorder promptRunRecorder;
    @Mock private PlanReviewService planReviewService;
    @Mock private ToolRegistry toolRegistry;
    @Mock private AgentProfile profile;

    private ToolUseLoopRuntime runtime;

    @BeforeEach
    void setUp() {
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(10))
                .build());
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .slidingWindowSize(10)
                .build());

        runtime = new ToolUseLoopRuntime(
                chatModel, toolExecutor, eventPublisher, chatMemory,
                promptAssembler, promptRunRecorder,
                Executors.newSingleThreadExecutor(),
                retryRegistry, cbRegistry,
                planReviewService, toolRegistry
        );

        // 设置 @Value 字段
        ReflectionTestUtils.setField(runtime, "maxTurns", 3);
        ReflectionTestUtils.setField(runtime, "maxRetries", 1);
        ReflectionTestUtils.setField(runtime, "llmTimeoutSeconds", 5);

        // 通用 mock
        lenient().when(profile.code()).thenReturn(AgentProfileCode.GENERAL);
        lenient().when(profile.enabledToolNames()).thenReturn(List.of("searchTool", "noteTool"));
        lenient().when(promptAssembler.assemble(any(), any())).thenReturn(
                new AssembledPrompt("system prompt", List.of(), "abc123", "def456", "ghi789"));
    }

    private AgentContext buildContext() {
        return AgentContext.builder()
                .requestId("req-001")
                .traceId("trace-001")
                .chatId("chat-001")
                .profile(profile)
                .userMessage("你好")
                .history(List.of())
                .build();
    }

    private ChatResponse mockResponse(String text, List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage msg = new AssistantMessage(text, java.util.Map.of(),
                toolCalls != null ? toolCalls : List.of());
        return new ChatResponse(List.of(new Generation(msg)));
    }

    // ── 测试用例 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("模型直接返回最终答案（无工具调用）→ 单轮结束")
    void directAnswerWithoutToolCalls() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(mockResponse("你好！有什么可以帮助你的？", null));

        AgentResponse response = runtime.execute(buildContext());

        assertNotNull(response);
        assertEquals("你好！有什么可以帮助你的？", response.answer());
        assertTrue(response.toolCalls().isEmpty());
        assertEquals("req-001", response.requestId());
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("模型返回 tool_use → 执行工具 → 第二轮返回最终答案")
    void toolUseLoopExecutesToolThenAnswers() {
        // 第一轮：模型请求调用 searchTool
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1", "function", "searchTool", "{\"query\":\"test\"}");
        ChatResponse firstTurn = mockResponse("", List.of(toolCall));

        // 第二轮：模型返回最终答案
        ChatResponse secondTurn = mockResponse("根据搜索结果，答案是42。", null);

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(firstTurn)
                .thenReturn(secondTurn);
        when(toolExecutor.execute(eq("searchTool"), eq("{\"query\":\"test\"}"),
                anyString(), anyString(), anyInt(), any()))
                .thenReturn(ToolResult.success("搜索结果：42"));

        AgentResponse response = runtime.execute(buildContext());

        assertNotNull(response);
        assertEquals("根据搜索结果，答案是42。", response.answer());
        assertEquals(1, response.toolCalls().size());
        assertEquals("searchTool", response.toolCalls().get(0).name());
        assertEquals("搜索结果：42", response.toolCalls().get(0).result());
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(toolExecutor, times(1)).execute(anyString(), anyString(),
                anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("工具不在 Profile 允许列表中 → 拒绝执行并返回错误信息")
    void toolNotInProfileIsRejected() {
        // 模型请求调用 disabledTool（不在 enabledToolNames 中）
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-2", "function", "disabledTool", "{}");
        ChatResponse firstTurn = mockResponse("", List.of(toolCall));
        ChatResponse secondTurn = mockResponse("抱歉，该工具不可用。", null);

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(firstTurn)
                .thenReturn(secondTurn);

        AgentResponse response = runtime.execute(buildContext());

        assertNotNull(response);
        assertEquals(1, response.toolCalls().size());
        assertTrue(response.toolCalls().get(0).result().contains("not available"));
        // 工具不应被实际执行
        verify(toolExecutor, never()).execute(anyString(), anyString(),
                anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("LLM 调用失败（返回 null）→ 降级 fallback 响应")
    void llmFailureReturnsFallback() {
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        AgentResponse response = runtime.execute(buildContext());

        assertNotNull(response);
        assertTrue(response.answer().contains("暂时不可用"));
        assertEquals("req-001", response.requestId());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("达到最大轮次限制 → 返回轮次限制提示")
    void maxTurnsReached() {
        // 每轮都返回工具调用，永远不给最终答案
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-loop", "function", "searchTool", "{}");
        ChatResponse toolTurn = mockResponse("", List.of(toolCall));

        when(chatModel.call(any(Prompt.class))).thenReturn(toolTurn);
        when(toolExecutor.execute(anyString(), anyString(),
                anyString(), anyString(), anyInt(), any()))
                .thenReturn(ToolResult.success("ok"));

        AgentResponse response = runtime.execute(buildContext());

        assertNotNull(response);
        assertTrue(response.answer().contains("最大轮次限制"));
        // maxTurns=3，应调用 3 次 LLM
        verify(chatModel, times(3)).call(any(Prompt.class));
    }
}
