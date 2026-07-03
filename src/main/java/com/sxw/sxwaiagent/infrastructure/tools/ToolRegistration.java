package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
import com.sxw.sxwaiagent.infrastructure.skill.SkillTool;
import com.sxw.sxwaiagent.infrastructure.rag.RagFlowKnowledgeService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
@EnableConfigurationProperties(TerminalOperationProperties.class)
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools(NoteSkill noteSkill,
                                   SkillTool skillTool,
                                   RagFlowKnowledgeService ragFlowKnowledgeService,
                                   TerminalOperationProperties terminalProperties) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(terminalProperties);
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        RagFlowSearchTool ragFlowSearchTool = new RagFlowSearchTool(ragFlowKnowledgeService);
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                ragFlowSearchTool,
                terminateTool,
                // 笔记技能，同时通过 MCP 对外暴露
                noteSkill,
                // Agent Skills 入口（listSkills / loadSkill），渐进式加载领域操作手册
                skillTool
        );
    }
}
