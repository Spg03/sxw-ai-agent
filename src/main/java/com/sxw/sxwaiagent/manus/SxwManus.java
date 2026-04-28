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
                You are SxwManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        String skillManifest = skillRegistry == null ? "" : skillRegistry.manifest();
        String systemPrompt = skillManifest.isEmpty()
                ? baseSystemPrompt
                : baseSystemPrompt + "\n" + skillManifest;
        this.setSystemPrompt(systemPrompt);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
