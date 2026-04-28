package com.sxw.sxwaiagent.infrastructure.skill;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 技能（Skill）装配。
 * <p>
 * 将技能 Bean 以 {@link ToolCallbackProvider} 暴露，Spring AI MCP Server 会自动把它们
 * 注册为 MCP 工具，供外部 Agent（如 Claude Desktop、Cursor、其他 LLM 应用）通过 SSE 调用。
 */
@Configuration
@EnableConfigurationProperties(NoteSkillProperties.class)
public class SkillConfig {

    @Bean
    public NoteSkill noteSkill(NoteSkillProperties properties) {
        return new NoteSkill(properties);
    }

    /**
     * 将 {@link NoteSkill} 上标注了 {@code @Tool} 的方法注册为 MCP 工具回调。
     * 这里复用项目已在使用的 {@link ToolCallbacks#from(Object...)} 扫描 @Tool 方法。
     */
    @Bean
    public ToolCallbackProvider noteSkillToolCallbackProvider(NoteSkill noteSkill) {
        return () -> ToolCallbacks.from(noteSkill);
    }

    /**
     * Agent Skills 入口工具（listSkills / loadSkill），同步暴露给本进程 Agent 和外部 MCP 客户端。
     */
    @Bean
    public SkillTool skillTool(SkillRegistry skillRegistry) {
        return new SkillTool(skillRegistry);
    }

    @Bean
    public ToolCallbackProvider skillToolCallbackProvider(SkillTool skillTool) {
        return () -> ToolCallbacks.from(skillTool);
    }
}
