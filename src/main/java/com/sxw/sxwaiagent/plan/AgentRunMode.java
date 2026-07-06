package com.sxw.sxwaiagent.plan;

/**
 * Agent 运行模式
 * 
 * - CHAT: 普通对话模式，使用默认工具集
 * - PLAN: 规划模式，只允许只读工具，用于任务分解和规划
 * - EXECUTE: 执行模式，允许执行工具，需要用户确认
 */
public enum AgentRunMode {
    CHAT,
    PLAN,
    EXECUTE
}
