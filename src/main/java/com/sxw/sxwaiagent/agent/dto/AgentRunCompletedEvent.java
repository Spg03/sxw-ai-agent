package com.sxw.sxwaiagent.agent.dto;

import org.springframework.context.ApplicationEvent;

/**
 * Agent 执行完成事件
 * <p>
 * 当 Agent 执行完成后发布此事件，供 Hermes 分析器异步处理。
 */
public class AgentRunCompletedEvent extends ApplicationEvent {
    
    private final String requestId;
    private final String traceId;
    private final String profileCode;
    private final AgentResponse response;
    
    public AgentRunCompletedEvent(Object source, String requestId, String traceId,
                                  String profileCode, AgentResponse response) {
        super(source);
        this.requestId = requestId;
        this.traceId = traceId;
        this.profileCode = profileCode;
        this.response = response;
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
    
    public AgentResponse getResponse() {
        return response;
    }
}
