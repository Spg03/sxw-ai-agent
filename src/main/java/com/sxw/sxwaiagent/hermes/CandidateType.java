package com.sxw.sxwaiagent.hermes;

/**
 * Hermes 候选类型
 * 
 * Agent 从对话中学习到的不同类型的经验总结
 */
public enum CandidateType {
    MEMORY,         // 用户偏好、重要事实
    KNOWLEDGE,      // 可复用的知识点
    EVAL_CASE,      // 测试用例
    PROMPT_HINT,    // Prompt 优化建议
    TOOL_PATTERN    // 工具使用模式
}
