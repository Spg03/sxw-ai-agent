package com.sxw.sxwaiagent.hermes;

/**
 * Hermes 候选类型
 */
public enum HermesCandidateType {
    /**
     * 记忆候选（用户偏好、反馈规则）
     */
    MEMORY,
    
    /**
     * 知识候选（缺失文档、FAQ）
     */
    KNOWLEDGE,
    
    /**
     * 评测用例（输入输出对）
     */
    EVAL_CASE,
    
    /**
     * Agent 规则（行为约束）
     */
    AGENT_RULE,
    
    /**
     * Prompt 优化建议
     */
    PROMPT_IMPROVEMENT,
    
    /**
     * 工具改进建议
     */
    TOOL_IMPROVEMENT,
    
    /**
     * 文档更新建议
     */
    DOC_UPDATE
}
