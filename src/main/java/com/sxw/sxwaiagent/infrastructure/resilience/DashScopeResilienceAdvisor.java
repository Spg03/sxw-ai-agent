package com.sxw.sxwaiagent.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 用 Resilience4j 给 DashScope 调用包装 Retry + RateLimiter + CircuitBreaker。
 * <p>
 * 装饰链（由内向外）：
 * <pre>
 *     LLM call → CircuitBreaker → Retry → RateLimiter
 * </pre>
 * 设计要点：
 * <ul>
 *   <li><b>CB 在 Retry 内层</b>：CB 一旦 OPEN，Retry 收到 {@code CallNotPermittedException}，
 *       由 yml 中 {@code ignore-exceptions} 立即放弃重试 → 故障快速失败，不再雪崩。</li>
 *   <li><b>RateLimiter 最外层</b>：限速决策先于任何业务执行。</li>
 *   <li><b>order = LOWEST_PRECEDENCE</b>：在 advisor 链最内，
 *       因此 {@code LlmAnswerCacheAdvisor} 命中时跳过本装饰器（缓存命中不消耗配额）。</li>
 * </ul>
 * 三个实例的指标 / 健康状态由 {@code resilience4j-spring-boot3} 自动接 Micrometer + Actuator。
 */
@Component
public class DashScopeResilienceAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(DashScopeResilienceAdvisor.class);
    private static final String INSTANCE = "dashscope";

    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public DashScopeResilienceAdvisor(RetryRegistry retryRegistry,
                                      RateLimiterRegistry rateLimiterRegistry,
                                      CircuitBreakerRegistry circuitBreakerRegistry) {
        this.retry = retryRegistry.retry(INSTANCE);
        this.rateLimiter = rateLimiterRegistry.rateLimiter(INSTANCE);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        this.retry.getEventPublisher()
                .onRetry(e -> log.warn("dashscope retry attempt={} lastError={}",
                        e.getNumberOfRetryAttempts(),
                        e.getLastThrowable() == null ? "n/a" : e.getLastThrowable().toString()));
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(e -> log.warn("dashscope CB state {} -> {}",
                        e.getStateTransition().getFromState(),
                        e.getStateTransition().getToState()));
    }

    @Override
    public String getName() {
        return "DashScopeResilienceAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        // 由内向外手动嵌套：CB → Retry → RateLimiter
        Supplier<ChatClientResponse> base = () -> chain.nextCall(req);
        Supplier<ChatClientResponse> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, base);
        Supplier<ChatClientResponse> withRetry = Retry.decorateSupplier(retry, withCb);
        Supplier<ChatClientResponse> withRateLimit = RateLimiter.decorateSupplier(rateLimiter, withRetry);
        return withRateLimit.get();
    }
}
