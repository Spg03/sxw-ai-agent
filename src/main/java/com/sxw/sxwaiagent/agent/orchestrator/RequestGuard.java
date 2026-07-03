package com.sxw.sxwaiagent.agent.orchestrator;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 请求守卫
 * <p>
 * 负责生成请求标识（requestId、traceId）和请求校验。
 */
@Component
public class RequestGuard {
    
    /**
     * 生成 traceId（用于追踪整个请求链路）
     */
    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 生成 requestId（用于标识唯一请求）
     */
    public String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}
