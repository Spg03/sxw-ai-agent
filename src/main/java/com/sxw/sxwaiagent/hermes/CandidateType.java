package com.sxw.sxwaiagent.hermes;

/**
 * Hermes 候选类型
 * 
 * Agent 从对话中学习到的不同类型的经验总结
 */
public enum CandidateType {
    MEMORY,                 // 用户偏好、重要事实
    KNOWLEDGE,              // 可复用的知识点
    EVAL_CASE,              // 测试用例
    PROMPT_HINT,            // Prompt 优化建议 (legacy, use PROMPT_IMPROVEMENT)
    TOOL_PATTERN,           // 工具使用模式 (legacy, use TOOL_IMPROVEMENT)
    AGENT_RULE,             // Agent 行为规则约束
    PROMPT_IMPROVEMENT,     // Prompt 改进建议
    TOOL_IMPROVEMENT,       // 工具改进建议
    DOC_UPDATE              // 文档更新建议
}
