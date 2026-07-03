package com.sxw.sxwaiagent.agent.profile;

import java.util.List;

/**
 * Agent Profile 接口
 * <p>
 * 定义 Agent 的业务配置，包括系统提示词、工具策略、记忆策略等。
 * 每个 Profile 代表一种 Agent 行为模式（如 LoveProfile、GeneralProfile）。
 */
public interface AgentProfile {
    
    /**
     * Profile 编码（唯一标识）
     */
    AgentProfileCode code();
    
    /**
     * 系统提示词
     */
    String systemPrompt();
    
    /**
     * 启用的工具名称列表
     */
    List<String> enabledToolNames();
    
    /**
     * 知识库范围
     */
    List<String> knowledgeScopes();
    
    /**
     * 记忆策略
     */
    MemoryPolicy memoryPolicy();
    
    /**
     * 工具策略
     */
    ToolPolicy toolPolicy();
    
    /**
     * 输出策略
     */
    OutputPolicy outputPolicy();
}
