package com.sxw.sxwaiagent.context;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Context assembler.
 * <p>
 * Assembles context by region and applies token budget control.
 * Assembly order:
 * 1. STATIC: System Rules + Tool Rules + Output Rules
 * 2. DYNAMIC: Profile Config + Memory + Knowledge + Tool Results + History + User Message
 */
@Component
public class ContextAssembler {
    
    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);
    
    private final ContextBudget budget;
    private final ContextCompressor compressor;
    private final TokenCounter tokenCounter;
    
    public ContextAssembler(ContextCompressor compressor, TokenCounter tokenCounter) {
        this.budget = ContextBudget.defaultBudget();
        this.compressor = compressor;
        this.tokenCounter = tokenCounter;
        log.info("ContextAssembler initialized with budget: available={} tokens", budget.availableTokens());
    }
    
    /**
     * Assemble complete context.
     *
     * @param context       Agent context
     * @param systemRules   System rules (static)
     * @param toolRules     Tool rules (static)
     * @param outputRules   Output rules (static)
     * @param memoryContent Memory content (dynamic)
     * @param knowledge     Knowledge retrieval results (dynamic)
     * @param toolResults   Tool execution results (dynamic)
     * @param history       Conversation history (dynamic)
     * @return Assembled message list
     */
    public List<Message> assemble(
            AgentContext context,
            String systemRules,
            String toolRules,
            String outputRules,
            String memoryContent,
            String knowledge,
            List<String> toolResults,
            List<Message> history
    ) {
        AgentProfile profile = context.profile();
        List<Message> messages = new ArrayList<>();
        int usedTokens = 0;
        
        // 1. Static region: System + Tool Rules + Output Rules
        String staticContent = buildStaticContent(systemRules, toolRules, outputRules);
        int staticTokens = tokenCounter.count(staticContent);
        
        if (staticTokens > budget.staticBudget()) {
            log.warn("Static content exceeds budget: {} > {}", staticTokens, budget.staticBudget());
            staticContent = compressor.compressStatic(staticContent, budget.staticBudget());
            staticTokens = tokenCounter.count(staticContent);
        }
        
        messages.add(createSystemMessage(staticContent));
        usedTokens += staticTokens;
        log.debug("Static region: {} tokens (budget={})", staticTokens, budget.staticBudget());
        
        // 2. Memory region
        if (memoryContent != null && !memoryContent.isEmpty()) {
            int memoryTokens = tokenCounter.count(memoryContent);
            if (memoryTokens > budget.memoryBudget()) {
                log.debug("Compressing memory: {} > {}", memoryTokens, budget.memoryBudget());
                memoryContent = compressor.compressMemory(memoryContent, budget.memoryBudget());
                memoryTokens = tokenCounter.count(memoryContent);
            }
            messages.add(createUserMessage("[memory summary]\n" + memoryContent));
            usedTokens += memoryTokens;
            log.debug("Memory region: {} tokens (budget={})", memoryTokens, budget.memoryBudget());
        }
        
        // 3. Knowledge region
        if (knowledge != null && !knowledge.isEmpty()) {
            int knowledgeTokens = tokenCounter.count(knowledge);
            if (knowledgeTokens > budget.knowledgeBudget()) {
                log.debug("Compressing knowledge: {} > {}", knowledgeTokens, budget.knowledgeBudget());
                knowledge = compressor.compressKnowledge(knowledge, budget.knowledgeBudget());
                knowledgeTokens = tokenCounter.count(knowledge);
            }
            messages.add(createUserMessage("[knowledge evidence]\n" + knowledge));
            usedTokens += knowledgeTokens;
            log.debug("Knowledge region: {} tokens (budget={})", knowledgeTokens, budget.knowledgeBudget());
        }
        
        // 4. Tool results region
        if (toolResults != null && !toolResults.isEmpty()) {
            String toolContent = String.join("\n---\n", toolResults);
            int toolTokens = tokenCounter.count(toolContent);
            if (toolTokens > budget.toolResultBudget()) {
                log.debug("Compressing tool results: {} > {}", toolTokens, budget.toolResultBudget());
                toolContent = compressor.compressToolResults(toolContent, budget.toolResultBudget());
                toolTokens = tokenCounter.count(toolContent);
            }
            messages.add(createUserMessage("[tool execution results]\n" + toolContent));
            usedTokens += toolTokens;
            log.debug("Tool results region: {} tokens (budget={})", toolTokens, budget.toolResultBudget());
        }
        
        // 5. Conversation history region
        if (history != null && !history.isEmpty()) {
            List<Message> compressedHistory = compressor.compressHistory(history, budget.historyBudget(), tokenCounter);
            messages.addAll(compressedHistory);
            int historyTokens = compressedHistory.stream()
                    .mapToInt(msg -> tokenCounter.count(msg.getText()))
                    .sum();
            usedTokens += historyTokens;
            log.debug("History region: {} tokens (budget={})", historyTokens, budget.historyBudget());
        }
        
        // 6. Current user message (no compression)
        String userMessage = context.userMessage();
        messages.add(createUserMessage(userMessage));
        int userTokens = tokenCounter.count(userMessage);
        usedTokens += userTokens;
        
        log.info("Context assembled: total={} tokens (available={}), requestId={}",
                usedTokens, budget.availableTokens(), context.requestId());
        
        if (budget.isOverBudget(usedTokens)) {
            log.warn("Context exceeds budget! total={} > available={}", usedTokens, budget.availableTokens());
        }
        
        return messages;
    }
    
    private String buildStaticContent(String systemRules, String toolRules, String outputRules) {
        StringBuilder sb = new StringBuilder();
        if (systemRules != null) sb.append(systemRules).append("\n\n");
        if (toolRules != null) sb.append(toolRules).append("\n\n");
        if (outputRules != null) sb.append(outputRules);
        return sb.toString();
    }
    
    private Message createSystemMessage(String content) {
        return new org.springframework.ai.chat.messages.SystemMessage(content);
    }
    
    private Message createUserMessage(String content) {
        return new org.springframework.ai.chat.messages.UserMessage(content);
    }
}
