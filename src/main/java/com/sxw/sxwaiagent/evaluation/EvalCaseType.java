package com.sxw.sxwaiagent.evaluation;

/**
 * 评测用例类型
 */
public enum EvalCaseType {
    /**
     * 工具调用测试
     */
    TOOL_CALL,
    
    /**
     * 知识检索测试
     */
    KNOWLEDGE_RETRIEVAL,
    
    /**
     * 对话流畅度测试
     */
    CONVERSATION,
    
    /**
     * 多轮对话测试
     */
    MULTI_TURN,
    
    /**
     * 边界条件测试
     */
    EDGE_CASE,
    
    /**
     * 性能测试
     */
    PERFORMANCE
}
