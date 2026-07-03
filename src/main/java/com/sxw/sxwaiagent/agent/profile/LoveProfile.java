package com.sxw.sxwaiagent.agent.profile;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 恋爱咨询专家 Profile
 * <p>
 * 基于现有 LoveApp 的业务逻辑，专注于恋爱心理咨询场景。
 */
@Component
public class LoveProfile implements AgentProfile {
    
    private static final String SYSTEM_PROMPT = """
            扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。
            围绕单身、恋爱、已婚三种状态提问：
            - 单身状态：询问社交圈拓展及追求心仪对象的困扰
            - 恋爱状态：询问沟通、习惯差异引发的矛盾
            - 已婚状态：询问家庭责任与亲属关系处理的问题
            
            引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。
            回答要温暖、专业、有同理心，避免说教。
            """;
    
    @Override
    public AgentProfileCode code() {
        return AgentProfileCode.LOVE;
    }
    
    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    @Override
    public List<String> enabledToolNames() {
        // LoveProfile 只允许只读工具（知识检索）
        return List.of("KnowledgeSearch", "NotesSearch");
    }
    
    @Override
    public List<String> knowledgeScopes() {
        return List.of("love", "relationship", "psychology");
    }
    
    @Override
    public MemoryPolicy memoryPolicy() {
        // 短期记忆，保留最近 20 条消息
        return MemoryPolicy.shortTerm(20);
    }
    
    @Override
    public ToolPolicy toolPolicy() {
        // 只读策略，不需要审批
        return ToolPolicy.readOnly();
    }
    
    @Override
    public OutputPolicy outputPolicy() {
        // 默认策略，包含引用
        return OutputPolicy.defaults();
    }
}
