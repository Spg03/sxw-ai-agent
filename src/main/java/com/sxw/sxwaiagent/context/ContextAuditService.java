package com.sxw.sxwaiagent.context;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptSection;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Writes metadata-only context audit rows. Exact model input is stored by ContextSnapshot. */
@Service
public class ContextAuditService {
    private final ContextItemRepository repository;
    private final TokenCounter tokens;

    public ContextAuditService(ContextItemRepository repository, TokenCounter tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    public void record(AgentContext context, int callNo, AssembledPrompt assembled,
                       String effectiveSystem, List<Message> actualMessages) {
        List<ContextItem> items = new ArrayList<>();
        for (PromptSection section : assembled.sections()) {
            String content = section.content() == null ? "" : section.content();
            String inclusion = inclusion(content, effectiveSystem);
            items.add(item(context, callNo, section.key(), section.type().name(), "PROMPT_SECTION", null,
                    inclusion, content, "prompt:" + section.key()));
        }
        for (int index = 0; index < actualMessages.size(); index++) {
            Message message = actualMessages.get(index);
            String text = message.getText() == null ? "" : message.getText();
            items.add(item(context, callNo, "MESSAGE_" + message.getMessageType().name(), "DYNAMIC",
                    "MESSAGE", Integer.toString(index), "INCLUDED", text, "message:" + index));
        }
        repository.saveAll(items);
    }

    private ContextItem item(AgentContext context, int callNo, String key, String kind,
                             String itemType, String itemId, String inclusion, String content, String source) {
        Long userId = context.metadata().get("userId") instanceof Number n ? n.longValue() : null;
        String preview = "[" + kind + " " + key + "; content withheld; " + content.length() + " chars]";
        return new ContextItem(null, context.requestId(), context.traceId(), context.chatId(), userId,
                callNo, key, kind, itemType, itemId, inclusion, sha256(content), content.length(), source,
                preview, tokens.count(content), LocalDateTime.now());
    }

    private static String inclusion(String content, String effectiveSystem) {
        if (content.isBlank()) return "EXCLUDED";
        if (effectiveSystem != null && effectiveSystem.contains(content)) return "INCLUDED";
        String prefix = content.substring(0, Math.min(80, content.length()));
        return effectiveSystem != null && effectiveSystem.contains(prefix) ? "PARTIAL" : "EXCLUDED";
    }

    public static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash context item", e);
        }
    }
}
