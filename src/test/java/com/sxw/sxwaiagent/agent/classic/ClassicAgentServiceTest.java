package com.sxw.sxwaiagent.agent.classic;

import com.sxw.sxwaiagent.love.LoveApp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassicAgentServiceTest {

    @Test
    void chatModeDelegatesToLoveAppChat() {
        LoveApp loveApp = mock(LoveApp.class);
        ClassicAgentService service = new ClassicAgentService(loveApp);
        when(loveApp.doChat("hello", "chat-1")).thenReturn("classic answer");

        String answer = service.chat("hello", "chat-1", "chat");

        assertEquals("classic answer", answer);
        verify(loveApp).doChat("hello", "chat-1");
    }

    @Test
    void ragflowModeDelegatesToLoveAppRagFlow() {
        LoveApp loveApp = mock(LoveApp.class);
        ClassicAgentService service = new ClassicAgentService(loveApp);
        when(loveApp.doChatWithRagFlow("hello", "chat-1")).thenReturn("rag answer");

        String answer = service.chat("hello", "chat-1", "ragflow");

        assertEquals("rag answer", answer);
        verify(loveApp).doChatWithRagFlow("hello", "chat-1");
    }
}
