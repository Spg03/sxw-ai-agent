package com.sxw.sxwaiagent.context;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptSection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextAuditServiceTest {
    @Test
    void storesHashesAndMetadataWithoutCopyingSensitiveContent() {
        ContextItemRepository repository = mock(ContextItemRepository.class);
        ContextAuditService service = new ContextAuditService(repository, new TokenCounter());
        String secret = "password=top-secret-value";
        List<PromptSection> sections = List.of(
                PromptSection.staticSection("SAFETY", "trusted", 1),
                PromptSection.dynamicSection("KNOWLEDGE", secret, 2));
        AssembledPrompt prompt = new AssembledPrompt("trusted\n" + secret, sections, "s", "d", "r");
        AgentContext context = AgentContext.builder().requestId("req").traceId("trace")
                .chatId("chat").metadata(Map.of("userId", 42L)).build();

        service.record(context, 1, prompt, "trusted", List.of(
                new SystemMessage("trusted"), new UserMessage(secret)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContextItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<ContextItem> items = captor.getValue();
        assertEquals(4, items.size());
        assertEquals("INCLUDED", items.getFirst().inclusionStatus());
        assertEquals("EXCLUDED", items.get(1).inclusionStatus());
        assertTrue(items.stream().allMatch(item -> !item.contentPreview().contains("top-secret-value")));
        assertTrue(items.stream().allMatch(item -> item.contentHash() != null && item.contentHash().length() == 64));
        assertEquals(42L, items.getFirst().userId());
    }
}
