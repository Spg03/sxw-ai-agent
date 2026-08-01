package com.sxw.sxwaiagent.agent.dto;

import java.util.List;

/**
 * Agent 统一响应
 *
 * @param requestId  请求 ID
 * @param traceId    追踪 ID
 * @param answer     Agent 回答
 * @param citations  引用列表
 * @param toolCalls  工具调用信息
 * @param latencyMs  延迟（毫秒）
 * @param errorKind  错误分类（正常响应时为 null）
 */
public record AgentResponse(
        String requestId,
        String traceId,
        String answer,
        List<String> citations,
        List<ToolCallInfo> toolCalls,
        long latencyMs,
        AgentErrorKind errorKind
) {
    
    /**
     * 工具调用信息
     */
    public record ToolCallInfo(
            String name,
            String arguments,
            String result
    ) {}
    
    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId;
        private String traceId;
        private String answer;
        private List<String> citations;
        private List<ToolCallInfo> toolCalls;
        private long latencyMs;
        private AgentErrorKind errorKind;
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }
        
        public Builder citations(List<String> citations) {
            this.citations = citations;
            return this;
        }
        
        public Builder toolCalls(List<ToolCallInfo> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }
        
        public Builder errorKind(AgentErrorKind errorKind) {
            this.errorKind = errorKind;
            return this;
        }
        
        public AgentResponse build() {
            return new AgentResponse(requestId, traceId, answer, citations, toolCalls, latencyMs, errorKind);
        }
    }
}
