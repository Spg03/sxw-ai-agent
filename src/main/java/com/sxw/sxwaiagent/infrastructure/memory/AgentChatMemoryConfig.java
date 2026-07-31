package com.sxw.sxwaiagent.infrastructure.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 系统共享对话记忆配置
 * <p>
 * 为 AgentOrchestrator 提供 ChatMemory bean，通过 JdbcChatMemoryRepository
 * 持久化到 PostgreSQL，保证重启后对话历史不丢失。
 */
@Configuration
public class AgentChatMemoryConfig {

    /**
     * Agent 系统共享的 ChatMemory
     * <p>
     * 使用 JdbcChatMemoryRepository + MessageWindowChatMemory，
     * 保留最近 20 条消息（10 轮对话）。
     */
    @Bean
    public ChatMemory agentChatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
