package com.sxw.sxwaiagent.agent.classic;

import com.sxw.sxwaiagent.love.LoveApp;
import org.springframework.stereotype.Service;

@Service
public class ClassicAgentService {

    private final LoveApp loveApp;

    public ClassicAgentService(LoveApp loveApp) {
        this.loveApp = loveApp;
    }

    public String chat(String message, String chatId, String mode) {
        String safeMode = mode == null || mode.isBlank() ? "chat" : mode;
        return switch (safeMode) {
            case "ragflow" -> loveApp.doChatWithRagFlow(message, chatId);
            case "rag" -> loveApp.doChatWithRag(message, chatId);
            case "tools" -> loveApp.doChatWithTools(message, chatId);
            case "mcp" -> loveApp.doChatWithMcp(message, chatId);
            default -> loveApp.doChat(message, chatId);
        };
    }
}
