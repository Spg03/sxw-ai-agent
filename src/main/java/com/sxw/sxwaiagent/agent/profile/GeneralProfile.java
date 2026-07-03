package com.sxw.sxwaiagent.agent.profile;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用任务助手 Profile
 * <p>
 * 基于现有 SxwManus 的业务逻辑，支持多种工具调用和复杂任务执行。
 */
@Component
public class GeneralProfile implements AgentProfile {
    
    private static final String SYSTEM_PROMPT = """
            You are SxwManus, a concise and pragmatic AI assistant. 默认使用中文回答。
            
            Response policy (must follow):
            1. For greetings, small talk, opinions, or general knowledge questions, 
               answer DIRECTLY in 1-3 short sentences. Do NOT call any tool.
            2. Only call tools when the user explicitly requests an action that 
               requires external information or file operations, such as:
               - "search for..." / "搜索..."
               - "scrape this URL" / "抓取这个网页"
               - "download..." / "下载..."
               - "generate a PDF..." / "生成 PDF..."
            3. When tools ARE called, minimize Thought. Prefer:
               "Thought: 执行 [工具名]."  (one line only)
            4. After the final tool result, give a brief summary (2-4 sentences). 
               Do NOT repeat raw tool output.
            5. When the task is complete, call the `terminate` tool to end the session.
            6. If a tool fails, briefly note the failure and try an alternative approach 
               if possible. Do not get stuck in infinite loops.
            
            Remember: Be concise. Save tokens. Only use tools when truly necessary.
            """;
    
    @Override
    public AgentProfileCode code() {
        return AgentProfileCode.GENERAL;
    }
    
    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    @Override
    public List<String> enabledToolNames() {
        // GeneralProfile 允许使用所有工具
        return List.of(
                "KnowledgeSearch",
                "WebSearch",
                "WebScraping",
                "ResourceDownload",
                "PDFGeneration",
                "RagFlowSearch",
                "NoteSkill",
                "SkillTool",
                "Terminate"
        );
    }
    
    @Override
    public List<String> knowledgeScopes() {
        return List.of("general", "technical", "documentation");
    }
    
    @Override
    public MemoryPolicy memoryPolicy() {
        // 长期记忆，保留最近 60 条消息
        return MemoryPolicy.longTerm(60);
    }
    
    @Override
    public ToolPolicy toolPolicy() {
        // 本地写策略，允许文件操作，需要审计
        return ToolPolicy.localWrite();
    }
    
    @Override
    public OutputPolicy outputPolicy() {
        // 默认策略，包含引用
        return OutputPolicy.defaults();
    }
}
