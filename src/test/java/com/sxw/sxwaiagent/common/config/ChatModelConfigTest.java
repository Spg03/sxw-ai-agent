package com.sxw.sxwaiagent.common.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatModelConfigTest {
    private final ChatModel primary = mock(ChatModel.class);
    private final ChatModel fallback = mock(ChatModel.class);
    private final ChatModel model = new ChatModelConfig.FallbackChatModel(primary, fallback,
            CircuitBreaker.ofDefaults("ollama-test"));
    private final ChatResponse answer = new ChatResponse(List.of(new Generation(new AssistantMessage("LOW"))));

    private Prompt detectorPrompt() {
        return new Prompt("Classify risk", ChatOptions.builder().model("qwen-turbo")
                .temperature(0.0).maxTokens(4).stopSequences(List.of("END")).build());
    }

    private Prompt providerBuild(Prompt input) {
        // Exercise the actual installed Alibaba request-options converter without HTTP.
        DashScopeChatModel provider = mock(DashScopeChatModel.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(provider, "defaultOptions",
                DashScopeChatOptions.builder().withModel("qwen-plus").build());
        return ReflectionTestUtils.invokeMethod(provider, "buildRequestPrompt", input);
    }

    @Test
    void genericDetectorOptionsSurviveActualDashScopeConversion() {
        when(primary.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prepared = providerBuild(invocation.getArgument(0));
            assertEquals("qwen-turbo", prepared.getOptions().getModel());
            assertEquals(4, prepared.getOptions().getMaxTokens());
            assertEquals(0.0, prepared.getOptions().getTemperature());
            // Alibaba exposes native stop via getStop(); its generic getter returns null.
            assertEquals(List.of("END"), ((DashScopeChatOptions) prepared.getOptions()).getStop());
            return answer;
        });
        assertSame(answer, model.call(detectorPrompt()));
        verifyNoInteractions(fallback);
    }

    @Test
    void streamingAlsoUsesNativeOptions() {
        when(primary.stream(any(Prompt.class))).thenAnswer(invocation -> {
            assertEquals(4, providerBuild(invocation.getArgument(0)).getOptions().getMaxTokens());
            return Flux.just(answer);
        });
        assertSame(answer, model.stream(detectorPrompt()).blockFirst());
        verifyNoInteractions(fallback);
    }

    @Test
    void preservesProviderSpecificToolsAndExecutionPolicy() {
        var options = DashScopeChatOptions.builder().withModel("qwen-plus").withEnableThinking(false)
                .withToolNames(Set.of("searchTool")).withInternalToolExecutionEnabled(false).build();
        Prompt prompt = new Prompt("Use tools", options);
        when(primary.call(prompt)).thenReturn(answer);
        assertSame(answer, model.call(prompt));
        verify(primary).call(same(prompt));
        assertFalse(options.getInternalToolExecutionEnabled());
    }

    @Test
    void realPrimaryFailureUsesOllamaDefaultsWithoutLeakingDashScopeModelName() {
        when(primary.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider unavailable"));
        when(fallback.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertInstanceOf(OllamaOptions.class, prompt.getOptions());
            assertNull(prompt.getOptions().getModel());
            assertEquals(4, prompt.getOptions().getMaxTokens());
            assertEquals("Classify risk", prompt.getContents());
            return answer;
        });
        assertSame(answer, model.call(detectorPrompt()));
    }

    @Test
    void asynchronousStreamFailureAlsoAdaptsFallbackOptions() {
        when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("offline")));
        when(fallback.stream(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertInstanceOf(OllamaOptions.class, prompt.getOptions());
            assertNull(prompt.getOptions().getModel());
            assertEquals(4, prompt.getOptions().getMaxTokens());
            return Flux.just(answer);
        });
        assertSame(answer, model.stream(detectorPrompt()).blockFirst());
    }
}
