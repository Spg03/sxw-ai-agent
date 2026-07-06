package com.sxw.sxwaiagent.agent.hermes;

import com.sxw.sxwaiagent.infrastructure.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HermesAgent {

    private static final String SYSTEM_PROMPT = """
            You are Hermes, a private tree-hole companion.
            Reply in Chinese. Your job is to listen, validate feelings, summarize gently,
            and provide one small next action. Do not call tools.
            Return a structured object with:
            - reply: warm companion response
            - emotionTag: one concise English emotion tag
            - summary: one-sentence private summary
            - suggestion: one small action suggestion
            """;

    private final ChatClient chatClient;

    public HermesAgent(@Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    public HermesReply comfort(String message, String chatId) {
        try {
            return chatClient.prompt()
                    .user(message)
                    .call()
                    .entity(HermesReply.class);
        } catch (RuntimeException e) {
            return new HermesReply(
                    "我在这里听你说。刚才这段内容对你来说并不轻松，我们可以先慢一点。",
                    "unknown",
                    message == null || message.isBlank() ? "用户还没有写下具体内容。" : abbreviate(message),
                    "先深呼吸一次，然后写下此刻最强烈的一个感受。"
            );
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 120) + "...";
    }
}
