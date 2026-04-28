package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.skill.Skill;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent Skills 调试 / 前端展示端点。
 * 不做权限控制（与现有 controller 风格一致），生产环境请放在内网或加 actuator 风格的网关。
 */
@RestController
@RequestMapping("/skills")
public class SkillController {

    @Resource
    private SkillRegistry skillRegistry;

    /**
     * 列出所有 skill 的 name + description（不含 body，便宜）。
     */
    @GetMapping
    public List<SkillSummary> list() {
        return skillRegistry.all().stream()
                .map(s -> new SkillSummary(s.name(), s.description()))
                .toList();
    }

    /**
     * 读取单个 skill 的完整 SKILL.md 正文（与 Agent 调 loadSkill 拿到的内容一致）。
     */
    @GetMapping("/{name}")
    public ResponseEntity<Skill> get(@PathVariable String name) {
        return skillRegistry.findByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record SkillSummary(String name, String description) {}
}
