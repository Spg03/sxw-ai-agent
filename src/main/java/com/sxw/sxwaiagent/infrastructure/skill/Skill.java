package com.sxw.sxwaiagent.infrastructure.skill;

/**
 * Anthropic Agent Skills 协议中的一个 skill 单元。
 * <p>
 * 一个 skill 对应 {@code classpath:skills/<name>/SKILL.md}，文件以 YAML frontmatter 开头：
 * <pre>
 * ---
 * name: love-counsel
 * description: 短描述，用于让 Agent 决定何时使用此 skill
 * ---
 * # Markdown 正文（操作手册）
 * </pre>
 * 渐进式加载：启动期所有 skill 的 description 会一次性注入 system prompt 供选择，
 * 完整 body 仅在 Agent 调用 {@code loadSkill(name)} 时按需返回。
 *
 * @param name        skill 标识，与目录名一致
 * @param description 一行摘要，写入 system prompt
 * @param body        SKILL.md 去掉 frontmatter 后的完整正文
 */
public record Skill(String name, String description, String body) {}
