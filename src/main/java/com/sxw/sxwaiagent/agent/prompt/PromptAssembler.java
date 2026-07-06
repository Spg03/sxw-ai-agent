package com.sxw.sxwaiagent.agent.prompt;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.memory.MemoryService;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt 动态组装器
 * 
 * 将 Prompt 拆分为静态和动态 Section，按需组装，并记录哈希用于版本追踪。
 * 
 * 静态 Section：
 * - ROLE: Agent 身份定义
 * - SAFETY: 安全红线
 * - TOOL_RULES: 工具使用规则
 * - OUTPUT_RULES: 输出格式要求
 * 
 * 动态 Section：
 * - PROFILE_CONTEXT: Profile 特定配置
 * - MEMORY_INDEX: 记忆索引摘要
 * - SELECTED_MEMORIES: 相关记忆详情
 * - KNOWLEDGE: 知识检索结果
 * - TOOL_RESULTS: 工具调用结果
 * - HISTORY: 对话历史
 * - USER_MESSAGE: 当前用户消息
 */
@Component
public class PromptAssembler {
    
    private static final Logger log = LoggerFactory.getLogger(PromptAssembler.class);
    
    // 静态 Section 顺序
    private static final int ORDER_ROLE = 10;
    private static final int ORDER_SAFETY = 20;
    private static final int ORDER_TOOL_RULES = 30;
    private static final int ORDER_OUTPUT_RULES = 40;
    
    // 动态 Section 顺序
    private static final int ORDER_PROFILE_CONTEXT = 100;
    private static final int ORDER_MEMORY_INDEX = 110;
    private static final int ORDER_SELECTED_MEMORIES = 120;
    private static final int ORDER_KNOWLEDGE = 130;
    private static final int ORDER_TOOL_RESULTS = 140;
    private static final int ORDER_HISTORY = 150;
    private static final int ORDER_USER_MESSAGE = 160;
    
    private final MemoryService memoryService;
    
    public PromptAssembler(MemoryService memoryService) {
        this.memoryService = memoryService;
    }
    
    /**
     * 组装完整的 System Prompt
     * 
     * @param profile Agent Profile
     * @param context Agent Context（包含记忆、知识、工具结果等）
     * @return 组装后的 Prompt
     */
    public AssembledPrompt assemble(AgentProfile profile, AgentContext context) {
        List<PromptSection> sections = new ArrayList<>();
        
        // 添加静态 Section
        sections.add(buildRoleSection(profile));
        sections.add(buildSafetySection());
        sections.add(buildToolRulesSection());
        sections.add(buildOutputRulesSection());
        
        // 添加动态 Section
        sections.add(buildProfileContextSection(profile));
        
        // 记忆相关
        if (profile.memoryPolicy() != null && !profile.memoryPolicy().enabledTypes().isEmpty()) {
            sections.add(buildMemoryIndexSection());
            
            // 如果有用户问题，加载相关记忆详情
            if (context.userMessage() != null && !context.userMessage().isBlank()) {
                sections.add(buildSelectedMemoriesSection(context.userMessage()));
            }
        }
        
        // 知识检索结果
        if (context.knowledgeResult() != null && !context.knowledgeResult().chunks().isEmpty()) {
            sections.add(buildKnowledgeSection(context.knowledgeResult()));
        }
        
        // 工具调用结果
        if (context.toolResults() != null && !context.toolResults().isEmpty()) {
            sections.add(buildToolResultsSection(context.toolResults()));
        }
        
        // 按顺序排序
        sections.sort(Comparator.comparingInt(PromptSection::order));
        
        // 组装最终 Prompt
        String rendered = sections.stream()
            .map(PromptSection::content)
            .filter(content -> content != null && !content.isBlank())
            .collect(Collectors.joining("\n\n"));
        
        // 计算哈希
        String staticHash = computeHash(
            sections.stream()
                .filter(PromptSection::isStatic)
                .map(PromptSection::content)
                .collect(Collectors.joining("\n"))
        );
        
        String dynamicHash = computeHash(
            sections.stream()
                .filter(PromptSection::isDynamic)
                .map(PromptSection::content)
                .collect(Collectors.joining("\n"))
        );
        
        String renderedHash = computeHash(rendered);
        
        log.debug("Assembled prompt: {} sections, rendered {} chars, static hash: {}, dynamic hash: {}",
            sections.size(), rendered.length(), staticHash.substring(0, 8), dynamicHash.substring(0, 8));
        
        return new AssembledPrompt(
            rendered,
            sections,
            staticHash,
            dynamicHash,
            renderedHash
        );
    }
    
    /**
     * 构建 ROLE Section
     */
    private PromptSection buildRoleSection(AgentProfile profile) {
        String content = String.format("""
            # Role
            
            You are %s.
            
            %s
            """,
            profile.code(),
            profile.systemPrompt() != null ? profile.systemPrompt() : ""
        );
        
        return PromptSection.staticSection("ROLE", content, ORDER_ROLE);
    }
    
    /**
     * 构建 SAFETY Section
     */
    private PromptSection buildSafetySection() {
        String content = """
            # Safety
            
            - Do not generate harmful, illegal, or unethical content
            - Respect user privacy and confidentiality
            - Do not execute destructive operations without explicit approval
            - Follow tool usage policies strictly
            """;
        
        return PromptSection.staticSection("SAFETY", content, ORDER_SAFETY);
    }
    
    /**
     * 构建 TOOL_RULES Section
     */
    private PromptSection buildToolRulesSection() {
        String content = """
            # Tool Usage Rules
            
            - Use tools only when necessary to answer the user's question
            - Prefer specialized tools over generic shell commands
            - Always check tool risk level before execution
            - Provide clear explanations of what you're doing and why
            """;
        
        return PromptSection.staticSection("TOOL_RULES", content, ORDER_TOOL_RULES);
    }
    
    /**
     * 构建 OUTPUT_RULES Section
     */
    private PromptSection buildOutputRulesSection() {
        String content = """
            # Output Rules
            
            - Provide clear, concise, and accurate responses
            - Use structured formats (lists, tables, code blocks) when appropriate
            - Cite knowledge sources when using retrieved information
            - Be transparent about limitations and uncertainties
            """;
        
        return PromptSection.staticSection("OUTPUT_RULES", content, ORDER_OUTPUT_RULES);
    }
    
    /**
     * 构建 PROFILE_CONTEXT Section
     */
    private PromptSection buildProfileContextSection(AgentProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Profile Configuration\n\n");
        
        if (profile.enabledToolNames() != null && !profile.enabledToolNames().isEmpty()) {
            sb.append("Available tools: ").append(String.join(", ", profile.enabledToolNames())).append("\n");
        }
        
        if (profile.knowledgeScopes() != null && !profile.knowledgeScopes().isEmpty()) {
            sb.append("Knowledge scopes: ").append(String.join(", ", profile.knowledgeScopes())).append("\n");
        }
        
        return PromptSection.dynamicSection("PROFILE_CONTEXT", sb.toString(), ORDER_PROFILE_CONTEXT);
    }
    
    /**
     * 构建 MEMORY_INDEX Section
     */
    private PromptSection buildMemoryIndexSection() {
        try {
            String indexText = memoryService.getMemoryIndexText();
            
            if (indexText == null || indexText.isBlank()) {
                return PromptSection.dynamicSection("MEMORY_INDEX", "", ORDER_MEMORY_INDEX);
            }
            
            String content = String.format("""
                # Active Memories
                
                %s
                """, indexText);
            
            return PromptSection.dynamicSection("MEMORY_INDEX", content, ORDER_MEMORY_INDEX);
        } catch (Exception e) {
            log.warn("Failed to build memory index section: {}", e.getMessage());
            return PromptSection.dynamicSection("MEMORY_INDEX", "", ORDER_MEMORY_INDEX);
        }
    }
    
    /**
     * 构建 SELECTED_MEMORIES Section
     */
    private PromptSection buildSelectedMemoriesSection(String userQuestion) {
        try {
            var memories = memoryService.selectRelevantMemories(userQuestion, 3);
            
            if (memories.isEmpty()) {
                return PromptSection.dynamicSection("SELECTED_MEMORIES", "", ORDER_SELECTED_MEMORIES);
            }
            
            String detailText = memoryService.getMemoryDetailText(memories);
            
            String content = String.format("""
                # Relevant Memory Details
                
                %s
                """, detailText);
            
            return PromptSection.dynamicSection("SELECTED_MEMORIES", content, ORDER_SELECTED_MEMORIES);
        } catch (Exception e) {
            log.warn("Failed to build selected memories section: {}", e.getMessage());
            return PromptSection.dynamicSection("SELECTED_MEMORIES", "", ORDER_SELECTED_MEMORIES);
        }
    }
    
    /**
     * 构建 KNOWLEDGE Section
     */
    private PromptSection buildKnowledgeSection(KnowledgeRetrievalResult result) {
        if (result == null || result.chunks().isEmpty()) {
            return PromptSection.dynamicSection("KNOWLEDGE", "", ORDER_KNOWLEDGE);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("# Retrieved Knowledge\n\n");
        
        for (var chunk : result.chunks()) {
            sb.append("## ").append(chunk.title()).append("\n");
            sb.append(chunk.content()).append("\n\n");
        }
        
        return PromptSection.dynamicSection("KNOWLEDGE", sb.toString(), ORDER_KNOWLEDGE);
    }
    
    /**
     * 构建 TOOL_RESULTS Section
     */
    private PromptSection buildToolResultsSection(List<com.sxw.sxwaiagent.agent.tool.ToolResult> results) {
        if (results == null || results.isEmpty()) {
            return PromptSection.dynamicSection("TOOL_RESULTS", "", ORDER_TOOL_RESULTS);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("# Tool Call Results\n\n");
        
        for (var result : results) {
            sb.append("## Tool: ").append(result.toolName()).append("\n");
            sb.append("Result: ").append(result.result()).append("\n\n");
        }
        
        return PromptSection.dynamicSection("TOOL_RESULTS", sb.toString(), ORDER_TOOL_RESULTS);
    }
    
    /**
     * 计算 SHA-256 哈希
     */
    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute hash", e);
            return "error";
        }
    }
}
