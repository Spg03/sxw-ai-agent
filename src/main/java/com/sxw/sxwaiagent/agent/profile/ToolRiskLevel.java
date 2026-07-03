package com.sxw.sxwaiagent.agent.profile;

/**
 * 工具风险等级枚举
 */
public enum ToolRiskLevel {
    
    /**
     * 只读操作，无副作用（KnowledgeSearch, WebSearch）
     */
    READ_ONLY,
    
    /**
     * 本地写操作（FileOperation, NoteSkill）
     */
    LOCAL_WRITE,
    
    /**
     * 外部写操作（API 调用、邮件发送）
     */
    EXTERNAL_WRITE,
    
    /**
     * 破坏性操作（删除文件、清空数据）
     */
    DESTRUCTIVE,
    
    /**
     * Shell 命令执行（TerminalOperation）
     */
    SHELL
}
