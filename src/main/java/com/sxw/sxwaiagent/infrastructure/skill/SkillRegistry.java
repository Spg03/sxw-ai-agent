package com.sxw.sxwaiagent.infrastructure.skill;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 启动期扫描 {@code classpath*:skills/&#42;/SKILL.md}，解析 YAML frontmatter，
 * 把所有 skill 加载到内存索引中。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>仅加载一次（启动期），运行期只读 — 线程安全</li>
 *   <li>解析失败的单个 skill 不影响其它 skill</li>
 *   <li>对外暴露三个能力：{@link #manifest()}（注入 system prompt 用）、
 *       {@link #findByName(String)}（{@code loadSkill} 工具用）、{@link #all()}</li>
 * </ul>
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private static final String LOCATION = "classpath*:skills/*/SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION);
        } catch (IOException e) {
            log.warn("Failed to scan skills location {}: {}", LOCATION, e.getMessage());
            return;
        }
        Yaml yaml = new Yaml();
        for (Resource res : resources) {
            try (InputStream in = res.getInputStream()) {
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Skill skill = parse(raw, yaml);
                if (skill == null) {
                    log.warn("Skipping invalid SKILL.md (missing frontmatter): {}", res.getURI());
                    continue;
                }
                if (skills.containsKey(skill.name())) {
                    log.warn("Duplicate skill name '{}' from {}, ignored.", skill.name(), res.getURI());
                    continue;
                }
                skills.put(skill.name(), skill);
            } catch (Exception e) {
                log.warn("Failed to load skill from {}: {}", safeUri(res), e.getMessage());
            }
        }
        log.info("SkillRegistry loaded {} skill(s): {}", skills.size(), skills.keySet());
    }

    /**
     * 注入 system prompt 的 skill 清单（渐进式加载的入口摘要）。
     */
    public String manifest() {
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("# 可用 Skill 清单（按需加载）\n");
        for (Skill s : skills.values()) {
            sb.append("- **").append(s.name()).append("**: ").append(s.description()).append('\n');
        }
        sb.append("\n要使用某个 skill，请调用工具 `loadSkill(name=\"<skill 名>\")` 获取完整操作手册后再执行。\n");
        return sb.toString();
    }

    public Optional<Skill> findByName(String name) {
        return Optional.ofNullable(name).map(skills::get);
    }

    public Collection<Skill> all() {
        return skills.values();
    }

    // ---------- internals ----------

    static Skill parse(String raw, Yaml yaml) {
        if (raw == null || !raw.startsWith(FRONTMATTER_DELIMITER)) {
            return null;
        }
        // skip first delimiter line
        int firstNl = raw.indexOf('\n');
        if (firstNl < 0) return null;
        int closing = raw.indexOf("\n" + FRONTMATTER_DELIMITER, firstNl);
        if (closing < 0) return null;
        String header = raw.substring(firstNl + 1, closing);
        // body starts after the closing delimiter line
        int bodyStart = raw.indexOf('\n', closing + 1);
        String body = bodyStart < 0 ? "" : raw.substring(bodyStart + 1);
        Map<String, Object> meta = yaml.load(header);
        if (meta == null) return null;
        Object n = meta.get("name");
        Object d = meta.get("description");
        if (n == null || d == null) return null;
        return new Skill(n.toString().trim(), d.toString().trim(), body.trim());
    }

    private static String safeUri(Resource res) {
        try {
            return res.getURI().toString();
        } catch (IOException e) {
            return res.getDescription();
        }
    }
}
