package com.sxw.sxwaiagent.manus;

import com.sxw.sxwaiagent.infrastructure.advisor.MyLoggerAdvisor;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * AI 超级智能体（拥有自主规划能力）。
 * <p>
 * 注意：该类持有会话级可变状态（messageList/state/currentStep 等），<b>不是线程安全的</b>。
 * 每次请求必须 {@code new} 一个新实例（参见 {@code AiController#doChatWithManus}），
 * 不要将其注册为 Spring 单例 Bean 被并发共享。
 */
public class SxwManus extends ToolCallAgent {

    public SxwManus(ToolCallback[] allTools, ChatModel dashscopeChatModel, SkillRegistry skillRegistry) {
        super(allTools);
        this.setName("sxwManus");
        // 基础 system prompt + Agent Skills 清单（progressive disclosure：只放摘要，正文按需通过 loadSkill 工具拉取）
        String baseSystemPrompt = """
                You are SxwManus, a concise and pragmatic AI assistant. 默认使用中文回答。

                Response policy (must follow):
                1. For greetings, small talk, opinions, or general knowledge questions, answer DIRECTLY in 1-3 short sentences. Do NOT call any tool. Do NOT call doTerminate either — simply produce the final assistant text and stop.
                2. Only call tools when the user explicitly requests an action that requires them (file/note operations, web search/scraping, downloads, terminal, PDF generation, MCP skills).
                3. Never chain redundant tools. For example, after createNote do NOT immediately readNote / listNotes unless the user asked. After a successful action, summarize the result in one sentence and call doTerminate.
                4. If a tool returns an empty / failed result, do NOT retry the same call with the same arguments. Either try a clearly different argument once, or explain to the user that it failed.
                5. Keep total tool calls per request as small as possible (ideally 0–2). When the task is fully done, call doTerminate.
                """;
        String skillManifest = skillRegistry == null ? "" : skillRegistry.manifest();
        String systemPrompt = skillManifest.isEmpty()
                ? baseSystemPrompt
                : baseSystemPrompt + "\n" + skillManifest;
        this.setSystemPrompt(systemPrompt);
        String NEXT_STEP_PROMPT = """
                Decide the minimal next action:
                - If you can answer the user from existing context, output the final answer in plain text and stop (do not call tools).
                - Otherwise, call exactly ONE tool that makes clear progress.
                - When the user's request is fully satisfied, call `doTerminate`.
                Avoid speculative or exploratory tool calls.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(8);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
