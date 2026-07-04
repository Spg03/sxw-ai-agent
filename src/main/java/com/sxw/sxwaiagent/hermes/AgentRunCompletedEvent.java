package com.sxw.sxwaiagent.hermes;

import org.springframework.context.ApplicationEvent;

/**
 * Agent 运行完成事件
 * <p>
 * 当 Agent 执行完成后发布此事件，触发 Hermes 异步分析。
 */
public class AgentRunCompletedEvent extends ApplicationEvent {
    
    private final String requestId;
    private final String traceId;
    private final String profileCode;
    private final int turnCount;
    private final boolean success;
    private final String summary;
    
    public AgentRunCompletedEvent(
        Object source,
        String requestId,
        String traceId,
        String profileCode,
        int turnCount,
        boolean success,
        String summary
    ) {
        super(source);
        this.requestId = requestId;
        this.traceId = traceId;
        this.profileCode = profileCode;
        this.turnCount = turnCount;
        this.success = success;
        this.summary = summary;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public String getProfileCode() {
        return profileCode;
    }
    
    public int getTurnCount() {
        return turnCount;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getSummary() {
        return summary;
    }
}
