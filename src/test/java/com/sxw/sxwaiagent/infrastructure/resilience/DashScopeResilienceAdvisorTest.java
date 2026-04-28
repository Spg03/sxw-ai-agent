package com.sxw.sxwaiagent.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashScopeResilienceAdvisorTest {

    private static ChatClientResponse okResp() {
        ChatResponse cr = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        return ChatClientResponse.builder().chatResponse(cr).build();
    }

    private static ChatClientRequest req() {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage("hi"))))
                .build();
    }

    private static CallAdvisorChain chainOf(java.util.function.Supplier<ChatClientResponse> supplier,
                                            AtomicInteger calls) {
        return new CallAdvisorChain() {
            @Override
            public @NonNull ChatClientResponse nextCall(@NonNull ChatClientRequest r) {
                calls.incrementAndGet();
                return Objects.requireNonNull(supplier.get());
            }
            @Override
            public @NonNull List<org.springframework.ai.chat.client.advisor.api.CallAdvisor> getCallAdvisors() {
                return Objects.requireNonNull(List.<org.springframework.ai.chat.client.advisor.api.CallAdvisor>of());
            }
        };
    }

    private DashScopeResilienceAdvisor build(RetryConfig retryCfg, RateLimiterConfig rlCfg, CircuitBreakerConfig cbCfg) {
        RetryRegistry rr = RetryRegistry.of(retryCfg);
        RateLimiterRegistry rlr = RateLimiterRegistry.of(rlCfg);
        CircuitBreakerRegistry cbr = CircuitBreakerRegistry.of(cbCfg);
        // 触发实例创建
        rr.retry("dashscope");
        rlr.rateLimiter("dashscope");
        cbr.circuitBreaker("dashscope");
        return new DashScopeResilienceAdvisor(rr, rlr, cbr);
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        DashScopeResilienceAdvisor advisor = build(
                RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build(),
                RateLimiterConfig.custom().limitForPeriod(1000).limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO).build(),
                CircuitBreakerConfig.custom().minimumNumberOfCalls(100).slidingWindowSize(100).build());

        AtomicInteger calls = new AtomicInteger();
        ChatClientResponse resp = advisor.adviseCall(req(), chainOf(() -> {
            if (calls.get() <= 2) throw new RuntimeException("transient");
            return okResp();
        }, calls));

        assertNotNull(resp);
        assertEquals(3, calls.get(), "first 2 attempts failed, 3rd succeeded");
    }

    @Test
    void rateLimiterRejectsExcessCalls() {
        DashScopeResilienceAdvisor advisor = build(
                RetryConfig.custom().maxAttempts(1).build(),
                RateLimiterConfig.custom().limitForPeriod(1).limitRefreshPeriod(Duration.ofSeconds(60))
                        .timeoutDuration(Duration.ZERO).build(),
                CircuitBreakerConfig.custom().minimumNumberOfCalls(100).build());

        AtomicInteger calls = new AtomicInteger();
        // 第一次放行
        advisor.adviseCall(req(), chainOf(DashScopeResilienceAdvisorTest::okResp, calls));
        // 第二次被限流器拒绝（同一刷新窗口内）
        assertThrows(RequestNotPermitted.class,
                () -> advisor.adviseCall(req(), chainOf(DashScopeResilienceAdvisorTest::okResp, calls)));
        assertEquals(1, calls.get(), "second call must be rejected before reaching downstream");
    }

    @Test
    void circuitBreakerOpensAfterFailures() {
        DashScopeResilienceAdvisor advisor = build(
                RetryConfig.custom().maxAttempts(1).build(),
                RateLimiterConfig.custom().limitForPeriod(1000).limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO).build(),
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build());

        AtomicInteger calls = new AtomicInteger();
        // 4 次连续失败 → 触发熔断
        for (int i = 0; i < 4; i++) {
            try {
                advisor.adviseCall(req(), chainOf(() -> { throw new RuntimeException("boom"); }, calls));
            } catch (RuntimeException ignored) {
                // 预期失败
            }
        }
        // 第 5 次应被 CB 拒绝（fast-fail），不再调下游
        int before = calls.get();
        assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                () -> advisor.adviseCall(req(), chainOf(DashScopeResilienceAdvisorTest::okResp, calls)));
        assertEquals(before, calls.get(), "CB open should fast-fail without invoking downstream");
    }
}
