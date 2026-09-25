package com.sxw.sxwaiagent.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationRunListenerTest {

    @Test
    void extractsOnlyExplicitRememberRequests() {
        assertEquals("我偏好简洁的中文回答", ConversationRunListener.extractExplicitMemory("请记住：我偏好简洁的中文回答。"));
        assertEquals("I prefer Java", ConversationRunListener.extractExplicitMemory("Please remember that I prefer Java!"));
        assertNull(ConversationRunListener.extractExplicitMemory("我之前说过我偏好简洁回答"));
        assertNull(ConversationRunListener.extractExplicitMemory("请忘记我的住址"));
    }

    @Test
    void rejectsOversizedExplicitMemoryBeforePersistence() {
        assertNull(ConversationRunListener.extractExplicitMemory("记住" + "很长".repeat(260)));
    }
}
