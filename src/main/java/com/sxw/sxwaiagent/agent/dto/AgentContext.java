package com.sxw.sxwaiagent.agent.dto;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.plan.AgentRunMode;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行上下文
 * <p>
 * 包含 Agent 执行所需的所有上下文信息：请求标识、Profile、用户消息、历史消息等。
 *
 * @param requestId 请求 ID
 * @param traceId   追踪 ID
 * @param chatId    会话 ID
 * @param profile   Agent Profile
 * @param userMessage 用户消息
 * @param history   历史消息列表
 * @param metadata  元数据
 * @param runMode   运行模式（默认 CHAT）
 * @param planId    关联的计划 ID（EXECUTE 模式下使用）
 */
public record AgentContext(
        String requestId,
        String traceId,
        String chatId,
        AgentProfile profile,
        String userMessage,
        List<Message> history,
        Map<String, Object> metadata,
        AgentRunMode runMode,
        String planId
) {
    
    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId;
        private String traceId;
        private String chatId;
        private AgentProfile profile;
        private String userMessage;
        private List<Message> history = List.of();
        private Map<String, Object> metadata = Map.of();
        private AgentRunMode runMode = AgentRunMode.CHAT;
        private String planId;
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }
        
        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }
        
        public Builder profile(AgentProfile profile) {
            this.profile = profile;
            return this;
        }
        
        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }
        
        public Builder history(List<Message> history) {
            this.history = history;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder runMode(AgentRunMode runMode) {
            this.runMode = runMode;
            return this;
        }
        
        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }
        
        public AgentContext build() {
            return new AgentContext(requestId, traceId, chatId, profile, userMessage, history, metadata, runMode, planId);
        }
    }
}
