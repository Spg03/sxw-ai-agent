package com.sxw.sxwaiagent.memory;

/**
 * 记忆类型
 */
public enum MemoryType {
    /**
     * 用户偏好（语言、风格、频率）
     */
    USER,
    
    /**
     * 用户反馈形成的规则（纠正、确认）
     */
    FEEDBACK,
    
    /**
     * 项目长期状态（技术栈、约定）
     */
    PROJECT,
    
    /**
     * 外部引用指针（文档链接、API 地址）
     */
    REFERENCE
}
