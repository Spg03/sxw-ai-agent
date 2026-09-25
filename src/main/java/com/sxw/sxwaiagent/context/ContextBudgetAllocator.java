package com.sxw.sxwaiagent.context;

import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.agent.prompt.PromptSection;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Single authority for token-aware context degradation. */
@Component
public class ContextBudgetAllocator {
    private static final Set<String> ESSENTIAL_DYNAMIC = Set.of("PROFILE_CONTEXT", "WORKING_MEMORY", "MEMORY_INDEX");

    private final ContextBudget budget;
    private final TokenCounter tokens;

    @Value("${sxw.agent.memory.max-recent-turns:10}")
    private int maxRecentTurns;
    @Value("${sxw.agent.memory.max-recent-events:40}")
    private int maxRecentMessages;

    public ContextBudgetAllocator(ContextBudget budget, TokenCounter tokens) {
        this.budget = budget;
        this.tokens = tokens;
    }

    /**
     * Preserves static policy/current input, then working memory, summary and complete
     * recent turns. Lower-priority dynamic sections are added only while budget remains.
     */
    public BudgetedContext fit(AssembledPrompt prompt, String current, List<Message> history,
                               List<Message> currentToolMessages) {
        List<PromptSection> selected = new ArrayList<>();
        for (PromptSection section : prompt.sections()) {
            if (section.isStatic() || ESSENTIAL_DYNAMIC.contains(section.key())) {
                selected.add(section);
            }
        }
        String essentialSystem = render(selected);
        int essentialTokens = tokens.count(essentialSystem) + tokens.count(current);
        if (essentialTokens > budget.availableTokens()) {
            return BudgetedContext.rejected(essentialTokens, budget.availableTokens());
        }

        int remaining = budget.availableTokens() - essentialTokens;
        Map<String, Integer> grants = new LinkedHashMap<>();

        remaining = addSections(prompt, selected, Set.of("CONVERSATION_SUMMARY"),
                Math.min(remaining, Math.max(256, budget.historyBudget() / 2)), grants, "summary", remaining);

        HistorySelection recent = selectCompleteTurns(history == null ? List.of() : history,
                Math.min(remaining, budget.historyBudget()));
        remaining -= recent.tokens();
        grants.put("history", recent.tokens());

        remaining = addSections(prompt, selected, Set.of("KNOWLEDGE"),
                Math.min(remaining, budget.knowledgeBudget()), grants, "knowledge", remaining);
        remaining = addSections(prompt, selected, Set.of("SELECTED_MEMORIES"),
                Math.min(remaining, budget.memoryBudget()), grants, "relevantMemory", remaining);
        remaining = addSections(prompt, selected, Set.of("TOOL_RESULTS", "ATTACHMENTS"),
                Math.min(remaining, budget.toolResultBudget()), grants, "toolData", remaining);

        List<Message> toolMessages = fitToolMessages(currentToolMessages == null ? List.of() : currentToolMessages,
                remaining);
        int toolTokens = messageTokens(toolMessages);
        grants.put("toolProtocolHistory", toolTokens);

        String actualSystem = render(selected);
        int used = tokens.count(actualSystem) + tokens.count(current) + recent.tokens() + toolTokens;
        return new BudgetedContext(false, actualSystem, recent.messages(), toolMessages,
                used, budget.availableTokens(), Map.copyOf(grants));
    }

    private int addSections(AssembledPrompt prompt, List<PromptSection> selected, Set<String> keys,
                            int categoryBudget, Map<String, Integer> grants, String grantKey, int remaining) {
        int available = Math.min(categoryBudget, remaining);
        int used = 0;
        for (PromptSection section : prompt.sections()) {
            if (!keys.contains(section.key()) || section.content() == null || section.content().isBlank()) continue;
            int wanted = tokens.count(section.content());
            int grant = Math.min(wanted, Math.max(0, available - used));
            if (grant <= 0) break;
            String content = grant < wanted ? tokens.truncate(section.content(), grant) : section.content();
            selected.add(new PromptSection(section.key(), section.type(), content, section.order()));
            used += tokens.count(content);
        }
        grants.put(grantKey, used);
        return remaining - used;
    }

    private HistorySelection selectCompleteTurns(List<Message> history, int tokenBudget) {
        if (history.isEmpty() || tokenBudget <= 0) return new HistorySelection(List.of(), 0);
        List<List<Message>> turns = groupTurns(history);
        List<Message> selected = new ArrayList<>();
        int used = 0;
        int selectedTurns = 0;
        for (int index = turns.size() - 1; index >= 0 && selectedTurns < maxRecentTurns; index--) {
            List<Message> turn = turns.get(index);
            if (selected.size() + turn.size() > maxRecentMessages) break;
            int wanted = messageTokens(turn);
            if (wanted > tokenBudget - used) {
                if (selected.isEmpty()) {
                    List<Message> compact = compactTurn(turn, tokenBudget - used);
                    selected.addAll(0, compact);
                    used += messageTokens(compact);
                }
                break;
            }
            selected.addAll(0, turn);
            used += wanted;
            selectedTurns++;
        }
        return new HistorySelection(List.copyOf(selected), used);
    }

    private List<List<Message>> groupTurns(List<Message> history) {
        List<List<Message>> turns = new ArrayList<>();
        List<Message> current = null;
        for (Message message : history) {
            if (message instanceof UserMessage) {
                current = new ArrayList<>();
                turns.add(current);
            }
            if (current != null) current.add(message);
        }
        return turns.stream().filter(turn -> turn.stream().anyMatch(UserMessage.class::isInstance)
                        && turn.stream().anyMatch(AssistantMessage.class::isInstance))
                .toList();
    }

    private List<Message> compactTurn(List<Message> turn, int tokenBudget) {
        if (tokenBudget <= 0) return List.of();
        int perMessage = Math.max(32, tokenBudget / Math.max(1, turn.size()));
        List<Message> compact = new ArrayList<>();
        for (Message message : turn) {
            String text = tokens.truncate(message.getText(), perMessage);
            compact.add(message instanceof UserMessage ? new UserMessage(text) : new AssistantMessage(text));
        }
        return compact;
    }

    private List<Message> fitToolMessages(List<Message> messages, int remaining) {
        if (messageTokens(messages) <= remaining) return messages;
        if (remaining <= 0) return List.of();
        // Standard tool protocol is an Assistant(tool-call) + ToolResponse pair.
        // Never retain an orphaned half-pair; older pairs are discarded first.
        List<Message> selected = new ArrayList<>();
        int used = 0;
        for (int end = messages.size(); end > 0; ) {
            int start = Math.max(0, end - 2);
            List<Message> pair = messages.subList(start, end);
            int wanted = messageTokens(pair);
            if (used + wanted > remaining) break;
            selected.addAll(0, pair);
            used += wanted;
            end = start;
        }
        return List.copyOf(selected);
    }

    private int messageTokens(List<Message> messages) {
        return messages.stream().mapToInt(message -> tokens.count(message.getText())).sum();
    }

    private String render(List<PromptSection> sections) {
        return sections.stream().sorted(Comparator.comparingInt(PromptSection::order))
                .map(PromptSection::content).filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    public int available() { return budget.availableTokens(); }

    public record BudgetedContext(boolean rejected, String system, List<Message> history,
                                  List<Message> toolMessages, int usedTokens, int limit,
                                  Map<String, Integer> grants) {
        static BudgetedContext rejected(int used, int limit) {
            return new BudgetedContext(true, "", List.of(), List.of(), used, limit, Map.of());
        }
    }
    private record HistorySelection(List<Message> messages, int tokens) { }
}
