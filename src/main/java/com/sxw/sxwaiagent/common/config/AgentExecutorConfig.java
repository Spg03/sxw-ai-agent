package com.sxw.sxwaiagent.common.config;

import com.sxw.sxwaiagent.infrastructure.advisor.MyLoggerAdvisor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 异步任务线程池。
 * <p>
 * 替代 {@code CompletableFuture.runAsync} 默认使用的 {@code ForkJoinPool.commonPool()}：
 * <ul>
 *   <li>有界任务队列，避免请求洪峰耗尽内存；</li>
 *   <li>{@code CallerRunsPolicy} 提供天然背压；</li>
 *   <li>命名线程便于排查；</li>
 *   <li>注册到 Micrometer，可在 Prometheus 看到队列深度 / 活跃线程等指标。</li>
 * </ul>
 * 顺带在容器启动时把 {@link MeterRegistry} 注入 {@link MyLoggerAdvisor}，
 * 让所有 {@code new MyLoggerAdvisor()} 实例自动具备 LLM 调用指标采集能力。
 */
@Configuration
public class AgentExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutorConfig.class);

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public AgentExecutorConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @PostConstruct
    public void wireAdvisorMetrics() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            MyLoggerAdvisor.setDefaultMeterRegistry(registry);
            log.info("MyLoggerAdvisor metrics enabled via MeterRegistry={}",
                    registry.getClass().getSimpleName());
        } else {
            log.info("No MeterRegistry available; MyLoggerAdvisor falls back to logging only");
        }
    }

    @Bean(name = "agentTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentTaskExecutor() {
        AtomicInteger idx = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4, 16,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "agent-task-" + idx.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            ExecutorServiceMetrics.monitor(registry, executor, "agentTaskExecutor");
        }
        return executor;
    }
}
