package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LlmAnswerCacheAdvisorTest {

    private Cache<String, ChatClientResponse> cache;
    private SimpleMeterRegistry registry;
    private LlmAnswerCacheAdvisor advisor;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();
        registry = new SimpleMeterRegistry();
        advisor = new LlmAnswerCacheAdvisor(cache, registry);
    }

    private static ChatClientResponse okResponse(String text) {
        ChatResponse cr = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        return ChatClientResponse.builder().chatResponse(cr).build();
    }

    private static ChatClientRequest req(String userMessage) {
        Prompt prompt = new Prompt(List.of(new UserMessage(userMessage)));
        return ChatClientRequest.builder().prompt(prompt).build();
    }

    /** 仅 mock {@link CallAdvisorChain#nextCall(ChatClientRequest)}；其他方法不会被调用。*/
    private static CallAdvisorChain stubChain(java.util.function.Function<ChatClientRequest, ChatClientResponse> fn,
                                              AtomicInteger calls) {
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest req) {
                calls.incrementAndGet();
                return fn.apply(req);
            }
            @Override
            public List<org.springframework.ai.chat.client.advisor.api.CallAdvisor> getCallAdvisors() {
                return List.of();
            }
        };
    }

    @Test
    void missThenHit_skipsLlmOnSecondCall() {
        AtomicInteger calls = new AtomicInteger();
        ChatClientResponse first = okResponse("hello world");

        ChatClientResponse r1 = advisor.adviseCall(req("ping"), stubChain(r -> first, calls));
        ChatClientResponse r2 = advisor.adviseCall(req("ping"), stubChain(r -> first, calls));

        assertSame(first, r1);
        assertSame(first, r2);
        assertEquals(1, calls.get(), "second identical request should hit cache and skip LLM");
        assertEquals(1.0, registry.counter("ai.chat.cache", "outcome", "hit").count());
        assertEquals(1.0, registry.counter("ai.chat.cache", "outcome", "miss-stored").count());
    }

    @Test
    void differentPromptsDoNotShareCache() {
        AtomicInteger calls = new AtomicInteger();
        advisor.adviseCall(req("hello"), stubChain(r -> okResponse("a"), calls));
        advisor.adviseCall(req("world"), stubChain(r -> okResponse("b"), calls));
        assertEquals(2, calls.get());
    }

    @Test
    void emptyResponseIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        advisor.adviseCall(req("foo"), stubChain(r -> okResponse(""), calls));
        advisor.adviseCall(req("foo"), stubChain(r -> okResponse(""), calls));
        assertEquals(2, calls.get(), "blank response must not be cached");
        assertEquals(2.0, registry.counter("ai.chat.cache", "outcome", "miss-skipped").count());
    }
}
