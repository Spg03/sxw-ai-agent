package com.sxw.sxwaiagent.agent.tool;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 工具执行指标钩子
 * <p>
 * 采集每次工具调用的耗时和成功/失败计数，暴露为 Micrometer 指标：
 * - tool.execution.timer（按工具名分 tag）
 * - tool.execution.success / tool.execution.failure
 */
@Component
@Order(0)
public class MetricsHook implements ToolHook {

    private final MeterRegistry meterRegistry;

    public MetricsHook(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void beforeExecution(String toolName, String arguments, String requestId) {
        // 计数在 afterExecution 中统一记录（需要知道成功/失败）
    }

    @Override
    public String afterExecution(String toolName, String result, long latencyMs, boolean success, String requestId) {
        // 记录耗时
        Timer.builder("tool.execution.timer")
            .tag("tool", toolName)
            .tag("status", success ? "success" : "failure")
            .register(meterRegistry)
            .record(latencyMs, TimeUnit.MILLISECONDS);

        // 记录计数
        Counter.builder(success ? "tool.execution.success" : "tool.execution.failure")
            .tag("tool", toolName)
            .register(meterRegistry)
            .increment();

        return null; // 不修改结果
    }
}
