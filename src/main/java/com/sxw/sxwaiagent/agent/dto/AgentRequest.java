package com.sxw.sxwaiagent.agent.dto;

import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Agent 统一请求
 *
 * @param chatId   会话 ID
 * @param profile  Profile 编码
 * @param message  用户消息
 * @param stream   是否流式输出
 * @param metadata 元数据（可选）
 */
public record AgentRequest(
        @NotBlank String chatId,
        @NotNull AgentProfileCode profile,
        @NotBlank String message,
        boolean stream,
        Map<String, Object> metadata
) {
    
    /**
     * 生成请求 ID（唯一标识本次请求）
     */
    public String requestId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 获取元数据值
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String chatId;
        private AgentProfileCode profile;
        private String message;
        private boolean stream = false;
        private Map<String, Object> metadata;
        
        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }
        
        public Builder profile(AgentProfileCode profile) {
            this.profile = profile;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public AgentRequest build() {
            return new AgentRequest(chatId, profile, message, stream, metadata);
        }
    }
}
