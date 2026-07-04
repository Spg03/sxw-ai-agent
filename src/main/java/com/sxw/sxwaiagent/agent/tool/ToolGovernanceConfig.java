package com.sxw.sxwaiagent.agent.tool;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 工具定义注册配置
 * <p>
 * 在应用启动时将所有工具的 ToolDefinition 注册到 ToolRegistry。
 * 与 ToolRegistration（Spring AI ToolCallback 注册）分离，
 * ToolRegistration 负责 Spring AI 层面的工具注册，
 * 此处负责治理层面的 ToolDefinition 注册。
 */
@Configuration
public class ToolGovernanceConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolGovernanceConfig.class);

    private final ToolRegistry toolRegistry;

    public ToolGovernanceConfig(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void registerAllToolDefinitions() {
        // READ_ONLY 工具
        toolRegistry.register(ToolDefinition.readOnly("ragFlowSearch", "RAGFlow 知识库检索"));
        toolRegistry.register(ToolDefinition.readOnly("webSearch", "网络搜索（SearchAPI）"));
        toolRegistry.register(ToolDefinition.readOnly("webScraping", "网页内容抓取"));

        // LOCAL_WRITE 工具
        toolRegistry.register(ToolDefinition.localWrite("fileOperation", "文件操作（沙箱内读写）"));
        toolRegistry.register(ToolDefinition.localWrite("pdfGeneration", "PDF 文件生成"));
        toolRegistry.register(ToolDefinition.localWrite("resourceDownload", "资源文件下载"));
        toolRegistry.register(ToolDefinition.localWrite("noteSkill", "笔记技能（创建/查询笔记）"));
        toolRegistry.register(ToolDefinition.localWrite("skillTool", "技能加载工具（列出/加载技能）"));

        // SHELL / DESTRUCTIVE 工具（需审批，默认关闭）
        toolRegistry.register(ToolDefinition.shell("terminalOperation", "终端命令执行"));
        toolRegistry.register(ToolDefinition.destructive("terminate", "终止当前执行"));

        log.info("Registered {} tool definitions in ToolRegistry", toolRegistry.size());
    }
}
