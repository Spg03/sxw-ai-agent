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
 * 上下文组装器
 * <p>
 * 按分区组装上下文，并应用 Token 预算控制。
 * 组装顺序：
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
     * 组装完整上下文
     *
     * @param context       Agent 上下文
     * @param systemRules   系统规则（静态）
     * @param toolRules     工具规则（静态）
     * @param outputRules   输出规则（静态）
     * @param memoryContent 记忆内容（动态）
     * @param knowledge     知识库检索结果（动态）
     * @param toolResults   工具执行结果（动态）
     * @param history       对话历史（动态）
     * @return 组装后的消息列表
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
        
        // 1. 静态区域：System + Tool Rules + Output Rules
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
        
        // 2. 记忆区域
        if (memoryContent != null && !memoryContent.isEmpty()) {
            int memoryTokens = tokenCounter.count(memoryContent);
            if (memoryTokens > budget.memoryBudget()) {
                log.debug("Compressing memory: {} > {}", memoryTokens, budget.memoryBudget());
                memoryContent = compressor.compressMemory(memoryContent, budget.memoryBudget());
                memoryTokens = tokenCounter.count(memoryContent);
            }
            messages.add(createUserMessage("[记忆摘要]\n" + memoryContent));
            usedTokens += memoryTokens;
            log.debug("Memory region: {} tokens (budget={})", memoryTokens, budget.memoryBudget());
        }
        
        // 3. 知识区域
        if (knowledge != null && !knowledge.isEmpty()) {
            int knowledgeTokens = tokenCounter.count(knowledge);
            if (knowledgeTokens > budget.knowledgeBudget()) {
                log.debug("Compressing knowledge: {} > {}", knowledgeTokens, budget.knowledgeBudget());
                knowledge = compressor.compressKnowledge(knowledge, budget.knowledgeBudget());
                knowledgeTokens = tokenCounter.count(knowledge);
            }
            messages.add(createUserMessage("[知识库证据]\n" + knowledge));
            usedTokens += knowledgeTokens;
            log.debug("Knowledge region: {} tokens (budget={})", knowledgeTokens, budget.knowledgeBudget());
        }
        
        // 4. 工具结果区域
        if (toolResults != null && !toolResults.isEmpty()) {
            String toolContent = String.join("\n---\n", toolResults);
            int toolTokens = tokenCounter.count(toolContent);
            if (toolTokens > budget.toolResultBudget()) {
                log.debug("Compressing tool results: {} > {}", toolTokens, budget.toolResultBudget());
                toolContent = compressor.compressToolResults(toolContent, budget.toolResultBudget());
                toolTokens = tokenCounter.count(toolContent);
            }
            messages.add(createUserMessage("[工具执行结果]\n" + toolContent));
            usedTokens += toolTokens;
            log.debug("Tool results region: {} tokens (budget={})", toolTokens, budget.toolResultBudget());
        }
        
        // 5. 对话历史区域
        if (history != null && !history.isEmpty()) {
            List<Message> compressedHistory = compressor.compressHistory(history, budget.historyBudget(), tokenCounter);
            messages.addAll(compressedHistory);
            int historyTokens = compressedHistory.stream()
                    .mapToInt(msg -> tokenCounter.count(msg.getText()))
                    .sum();
            usedTokens += historyTokens;
            log.debug("History region: {} tokens (budget={})", historyTokens, budget.historyBudget());
        }
        
        // 6. 当前用户消息（不压缩）
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
