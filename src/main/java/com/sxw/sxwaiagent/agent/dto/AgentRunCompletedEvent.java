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
    private final String chatId;
    private final String userMessage;
    
    /**
     * 完整构造器
     */
    public AgentRunCompletedEvent(Object source, String requestId, String traceId,
                                  String profileCode, AgentResponse response,
                                  String chatId, String userMessage) {
        super(source);
        this.requestId = requestId;
        this.traceId = traceId;
        this.profileCode = profileCode;
        this.response = response;
        this.chatId = chatId;
        this.userMessage = userMessage;
    }
    
    /**
     * 兼容旧构造器（chatId/userMessage 为 null）
     */
    public AgentRunCompletedEvent(Object source, String requestId, String traceId,
                                  String profileCode, AgentResponse response) {
        this(source, requestId, traceId, profileCode, response, null, null);
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
    
    public String getChatId() {
        return chatId;
    }
    
    public String getUserMessage() {
        return userMessage;
    }
}
