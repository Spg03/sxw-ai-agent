package com.sxw.sxwaiagent.manus;

import com.sxw.sxwaiagent.infrastructure.advisor.MyLoggerAdvisor;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * AI super agent with autonomous planning capabilities.
 *
 * Note: holds session-level mutable state (messageList/state/currentStep etc.); <b>NOT thread-safe</b>.
 * Each request must {@code new} an instance (see {@code AiController#doChatWithManus});
 * do NOT register as Spring singleton bean for concurrent sharing.
 */
public class SxwManus extends ToolCallAgent {

    public SxwManus(ToolCallback[] allTools, ChatModel dashscopeChatModel, SkillRegistry skillRegistry) {
        super(allTools);
        this.setName("sxwManus");
        // Base system prompt + Agent Skills manifest (progressive disclosure: summary only, full content loaded via loadSkill tool on demand)
        String baseSystemPrompt = """
                You are SxwManus, a concise and pragmatic AI assistant. Default response language: Chinese.

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
        // Initialize AI chat client
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
