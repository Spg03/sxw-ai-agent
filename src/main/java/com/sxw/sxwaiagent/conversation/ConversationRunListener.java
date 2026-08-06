package com.sxw.sxwaiagent.conversation;

import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConversationRunListener {
    private final ConversationService conversations;
    public ConversationRunListener(ConversationService conversations) { this.conversations = conversations; }
    @EventListener
    public void onCompleted(AgentRunCompletedEvent event) {
        if (event.getChatId() == null || event.getChatId().isBlank() || event.getResponse() == null || event.getResponse().answer() == null) return;
        try { conversations.appendAssistant(event.getChatId(), event.getResponse().answer()); }
        catch (Exception ignored) { /* legacy calls may not have a v2 conversation */ }
    }
}
