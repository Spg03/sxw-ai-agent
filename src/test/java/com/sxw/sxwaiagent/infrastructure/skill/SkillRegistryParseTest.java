package com.sxw.sxwaiagent.infrastructure.skill;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯单测：SKILL.md frontmatter 解析。无 Spring 上下文，跑得快。
 */
class SkillRegistryParseTest {

    private final Yaml yaml = new Yaml();

    @Test
    void parses_valid_frontmatter_and_body() {
        String raw = """
                ---
                name: love-counsel
                description: 处理情侣关系咨询
                ---
                # Body Title
                step 1
                """;
        Skill s = SkillRegistry.parse(raw, yaml);
        assertNotNull(s);
        assertEquals("love-counsel", s.name());
        assertEquals("处理情侣关系咨询", s.description());
        assertTrue(s.body().startsWith("# Body Title"));
        assertTrue(s.body().contains("step 1"));
    }

    @Test
    void returns_null_when_frontmatter_missing() {
        assertNull(SkillRegistry.parse("# just markdown\nno frontmatter", yaml));
        assertNull(SkillRegistry.parse("", yaml));
        assertNull(SkillRegistry.parse(null, yaml));
    }

    @Test
    void returns_null_when_required_keys_missing() {
        String noName = """
                ---
                description: only desc
                ---
                body
                """;
        assertNull(SkillRegistry.parse(noName, yaml));

        String noDesc = """
                ---
                name: x
                ---
                body
                """;
        assertNull(SkillRegistry.parse(noDesc, yaml));
    }

    @Test
    void empty_body_is_allowed() {
        String raw = """
                ---
                name: x
                description: y
                ---
                """;
        Skill s = SkillRegistry.parse(raw, yaml);
        assertNotNull(s);
        assertEquals("", s.body());
    }
}
