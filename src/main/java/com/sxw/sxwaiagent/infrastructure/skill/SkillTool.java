package com.sxw.sxwaiagent.infrastructure.skill;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.stream.Collectors;

/**
 * 把 {@link SkillRegistry} 以两个 {@code @Tool} 方法暴露给 Agent，并通过 MCP 透传给外部客户端。
 * <p>
 * 这两个工具实现了 Anthropic Agent Skills 的 progressive disclosure：
 * <ul>
 *   <li>{@link #listSkills()} — 列摘要，便宜，启动期已注入 system prompt，但仍保留作为可主动重读的工具</li>
 *   <li>{@link #loadSkill(String)} — 命中后才把整篇 SKILL.md 加载进对话上下文</li>
 * </ul>
 */
public class SkillTool {

    private final SkillRegistry registry;

    public SkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Tool(description = "List all available skills with their names and short descriptions. " +
            "Use this when you need to recall what skills are available before deciding which to load.")
    public String listSkills() {
        if (registry.all().isEmpty()) {
            return "no skills registered";
        }
        return registry.all().stream()
                .map(s -> "- " + s.name() + ": " + s.description())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Load the full SKILL.md content (operating manual) for the given skill name. " +
            "Call this only after deciding a skill is relevant to the user's task; the returned text " +
            "should be followed step by step. If the skill does not exist, an error string is returned.")
    public String loadSkill(@ToolParam(description = "Skill name as listed by listSkills, e.g. 'love-counsel'") String name) {
        return registry.findByName(name)
                .map(s -> "# Skill: " + s.name() + "\n" + s.body())
                .orElse("failed: skill not found: " + name);
    }
}
