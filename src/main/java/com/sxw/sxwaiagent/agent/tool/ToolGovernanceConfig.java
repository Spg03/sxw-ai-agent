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
        // READ_ONLY 工具（对应实际 @Tool 方法名）
        toolRegistry.register(ToolDefinition.readOnly("searchRagFlow", "RAGFlow 知识库检索"));
        toolRegistry.register(ToolDefinition.readOnly("searchWeb", "网络搜索（SearchAPI）"));
        toolRegistry.register(ToolDefinition.readOnly("scrapeWebPage", "网页内容抓取"));

        // LOCAL_WRITE 工具
        toolRegistry.register(ToolDefinition.localWrite("readFile", "文件读取（沙箱内）"));
        toolRegistry.register(ToolDefinition.localWrite("writeFile", "文件写入（沙箱内）"));
        toolRegistry.register(ToolDefinition.localWrite("generatePDF", "PDF 文件生成"));
        toolRegistry.register(ToolDefinition.localWrite("downloadResource", "资源文件下载"));
        toolRegistry.register(ToolDefinition.localWrite("createNote", "笔记创建"));
        toolRegistry.register(ToolDefinition.localWrite("appendNote", "笔记追加"));
        toolRegistry.register(ToolDefinition.localWrite("readNote", "笔记读取"));
        toolRegistry.register(ToolDefinition.localWrite("listNotes", "笔记列表"));
        toolRegistry.register(ToolDefinition.localWrite("searchNotes", "笔记搜索"));
        toolRegistry.register(ToolDefinition.localWrite("deleteNote", "笔记删除"));
        toolRegistry.register(ToolDefinition.localWrite("listSkills", "技能列表"));
        toolRegistry.register(ToolDefinition.localWrite("loadSkill", "技能加载"));

        // SHELL / DESTRUCTIVE 工具（需审批，默认关闭）
        toolRegistry.register(ToolDefinition.shell("executeTerminalCommand", "终端命令执行"));
        toolRegistry.register(ToolDefinition.destructive("doTerminate", "终止当前执行"));

        log.info("Registered {} tool definitions in ToolRegistry", toolRegistry.size());
    }
}
