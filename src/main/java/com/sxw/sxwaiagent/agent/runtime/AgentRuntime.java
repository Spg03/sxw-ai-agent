package com.sxw.sxwaiagent.agent.runtime;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;

/**
 * Agent 统一运行时接口
 * <p>
 * 定义 Agent 执行的核心契约，所有运行时实现都必须遵循此接口。
 */
public interface AgentRuntime {
    
    /**
     * 执行 Agent 任务
     *
     * @param context Agent 执行上下文
     * @return Agent 响应结果
     */
    AgentResponse execute(AgentContext context);
}
