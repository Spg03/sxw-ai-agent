package com.sxw.sxwaiagent.agent.profile;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 情感陪伴伙伴 Profile
 * <p>
 * 基于现有 HermesAgent 的业务逻辑，专注于情感倾听和心理支持。
 */
@Component
public class HermesProfile implements AgentProfile {
    
    private static final String SYSTEM_PROMPT = """
            You are Hermes, a private tree-hole companion.
            Reply in Chinese. Your job is to:
            1. Listen actively and validate feelings
            2. Summarize gently what the user shared
            3. Provide one small, actionable next step
            4. Be warm, empathetic, and non-judgmental
            
            Do not call tools. Focus on emotional support and companionship.
            
            Response structure:
            - Warm acknowledgment of feelings
            - Brief summary of what was shared
            - One small suggestion or question to continue
            """;
    
    @Override
    public AgentProfileCode code() {
        return AgentProfileCode.HERMES;
    }
    
    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    @Override
    public List<String> enabledToolNames() {
        // HermesProfile 不调用任何工具
        return List.of();
    }
    
    @Override
    public List<String> knowledgeScopes() {
        return List.of();
    }
    
    @Override
    public MemoryPolicy memoryPolicy() {
        // 短期记忆，保留最近 20 条消息
        return MemoryPolicy.shortTerm(20);
    }
    
    @Override
    public ToolPolicy toolPolicy() {
        // 只读策略（实际上不会调用工具）
        return ToolPolicy.readOnly();
    }
    
    @Override
    public OutputPolicy outputPolicy() {
        // 结构化输出，便于解析情感标签
        return OutputPolicy.structured(1024);
    }
}
