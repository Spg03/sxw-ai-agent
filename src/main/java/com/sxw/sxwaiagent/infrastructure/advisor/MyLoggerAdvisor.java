package com.sxw.sxwaiagent.infrastructure.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 自定义日志 / 观测 Advisor。
 * <p>
 * 输出结构化 key=value 日志，便于后续接入 ELK / Loki：
 * <pre>
 *   trace=... phase=request size=...
 *   trace=... phase=response latencyMs=... promptTokens=... completionTokens=... totalTokens=... finishReason=...
 * </pre>
 * 不再打印完整 prompt / response 正文（避免泄漏隐私 + 日志过载），
 * 只记录长度与可观测元数据；如需排查可临时打开 DEBUG。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    private static final ThreadLocal<Long> START_NS = new ThreadLocal<>();

    /**
     * 进程级默认 MeterRegistry，由 Spring 启动时通过
     * {@link #setDefaultMeterRegistry(MeterRegistry)} 注入；
     * 这样存量代码中 {@code new MyLoggerAdvisor()} 也会自动产生指标。
     */
    private static volatile MeterRegistry defaultMeterRegistry;

    public static void setDefaultMeterRegistry(MeterRegistry registry) {
        defaultMeterRegistry = registry;
    }

    /** 可选的 Micrometer 注册表；为空时降级为仅日志。*/
    private final MeterRegistry meterRegistry;

    public MyLoggerAdvisor() {
        this(defaultMeterRegistry);
    }

    public MyLoggerAdvisor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : defaultMeterRegistry;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ChatClientRequest before(ChatClientRequest request) {
        String trace = UUID.randomUUID().toString().substring(0, 8);
        TRACE.set(trace);
        START_NS.set(System.nanoTime());
        int promptSize = request.prompt() == null || request.prompt().getInstructions() == null
                ? 0 : request.prompt().getInstructions().size();
        log.info("ai trace={} phase=request messages={}", trace, promptSize);
        if (log.isDebugEnabled()) {
            log.debug("ai trace={} prompt={}", trace, request.prompt());
        }
        return request;
    }

    private void observeAfter(ChatClientResponse chatClientResponse) {
        String trace = TRACE.get();
        Long start = START_NS.get();
        long latencyMs = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
        try {
            ChatResponse cr = chatClientResponse.chatResponse();
            String finishReason = "unknown";
            long pTok = -1, cTok = -1, tTok = -1;
            if (cr != null) {
                if (cr.getResult() != null && cr.getResult().getMetadata() != null
                        && cr.getResult().getMetadata().getFinishReason() != null) {
                    finishReason = cr.getResult().getMetadata().getFinishReason();
                }
                ChatResponseMetadata meta = cr.getMetadata();
                if (meta != null && meta.getUsage() != null) {
                    Usage u = meta.getUsage();
                    pTok = nz(u.getPromptTokens());
                    cTok = nz(u.getCompletionTokens());
                    tTok = nz(u.getTotalTokens());
                }
            }
            log.info("ai trace={} phase=response latencyMs={} promptTokens={} completionTokens={} totalTokens={} finishReason={}",
                    trace, latencyMs, pTok, cTok, tTok, finishReason);
            if (log.isDebugEnabled() && cr != null && cr.getResult() != null) {
                log.debug("ai trace={} response={}", trace, cr.getResult().getOutput().getText());
            }
            recordMetrics(latencyMs, pTok, cTok, tTok, finishReason, "ok");
        } finally {
            TRACE.remove();
            START_NS.remove();
        }
    }

    private void recordMetrics(long latencyMs, long pTok, long cTok, long tTok,
                               String finishReason, String outcome) {
        if (meterRegistry == null) return;
        Timer.builder("ai.chat.latency")
                .description("LLM chat call latency")
                .tag("outcome", outcome)
                .tag("finishReason", finishReason)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
        if (pTok >= 0) incrCounter("ai.chat.tokens", pTok, "kind", "prompt", "outcome", outcome);
        if (cTok >= 0) incrCounter("ai.chat.tokens", cTok, "kind", "completion", "outcome", outcome);
        if (tTok >= 0) incrCounter("ai.chat.tokens", tTok, "kind", "total", "outcome", outcome);
        Counter.builder("ai.chat.calls")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void incrCounter(String name, long amount, String... tags) {
        Counter.builder(name).tags(tags).register(meterRegistry).increment(amount);
    }

    private static long nz(Integer v) {
        return v == null ? -1 : v.longValue();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        try {
            ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
            observeAfter(chatClientResponse);
            return chatClientResponse;
        } catch (RuntimeException e) {
            Long start = START_NS.get();
            long latencyMs = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
            log.warn("ai trace={} phase=error latencyMs={} message={}", TRACE.get(), latencyMs, e.getMessage());
            recordMetrics(latencyMs, -1, -1, -1, "error", "error");
            TRACE.remove();
            START_NS.remove();
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
    }
}
