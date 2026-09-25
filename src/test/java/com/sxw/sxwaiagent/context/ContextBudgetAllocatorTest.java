package com.sxw.sxwaiagent.context;

import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptSection;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextBudgetAllocatorTest {
    @Test
    void rejectsWhenMandatoryPolicyAndCurrentMessageExceedBudget() {
        ContextBudgetAllocator allocator = allocator(120, 20);
        AssembledPrompt prompt = prompt("S".repeat(500), "", "", "");

        ContextBudgetAllocator.BudgetedContext result = allocator.fit(prompt, "current", List.of(), List.of());

        assertTrue(result.rejected());
        assertTrue(result.usedTokens() > result.limit());
    }

    @Test
    void keepsOnlyCompleteRecentTurns() {
        ContextBudgetAllocator allocator = allocator(420, 40);
        List<Message> history = List.of(
                new UserMessage("old question " + "x".repeat(300)),
                new AssistantMessage("old answer " + "y".repeat(300)),
                new UserMessage("recent question"),
                new AssistantMessage("recent answer"));

        ContextBudgetAllocator.BudgetedContext result = allocator.fit(
                prompt("policy", "summary", "memory", "tools"), "current", history, List.of());

        assertFalse(result.rejected());
        assertFalse(result.history().isEmpty());
        assertInstanceOf(UserMessage.class, result.history().getFirst());
        assertInstanceOf(AssistantMessage.class, result.history().getLast());
        assertEquals(0, result.history().size() % 2);
        assertTrue(result.usedTokens() <= result.limit());
    }

    @Test
    void dropsLowPriorityToolDataBeforeMandatorySections() {
        ContextBudgetAllocator allocator = allocator(260, 40);
        AssembledPrompt prompt = prompt("trusted policy", "short summary", "pinned boundary",
                "tool output ".repeat(200));

        ContextBudgetAllocator.BudgetedContext result = allocator.fit(prompt, "current", List.of(), List.of());

        assertFalse(result.rejected());
        assertTrue(result.system().contains("trusted policy"));
        assertTrue(result.system().contains("pinned boundary"));
        assertTrue(result.grants().getOrDefault("toolData", 0) < 2000);
        assertTrue(result.usedTokens() <= result.limit());
    }

    private ContextBudgetAllocator allocator(int max, int reserved) {
        ContextBudgetAllocator allocator = new ContextBudgetAllocator(ContextBudget.of(max, reserved), new TokenCounter());
        ReflectionTestUtils.setField(allocator, "maxRecentTurns", 10);
        ReflectionTestUtils.setField(allocator, "maxRecentMessages", 40);
        return allocator;
    }

    private AssembledPrompt prompt(String policy, String summary, String memory, String tools) {
        List<PromptSection> sections = List.of(
                PromptSection.staticSection("SAFETY", policy, 10),
                PromptSection.dynamicSection("CONVERSATION_SUMMARY", summary, 20),
                PromptSection.dynamicSection("MEMORY_INDEX", memory, 30),
                PromptSection.dynamicSection("TOOL_RESULTS", tools, 40));
        return new AssembledPrompt(policy + summary + memory + tools, sections, "static", "dynamic", "rendered");
    }
}
