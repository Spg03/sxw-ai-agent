package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.skill.Skill;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent Skills 查询端点。鉴权由全局 SecurityFilterChain 统一执行；
 * 此接口只暴露已通过后端审核并装载到 SkillRegistry 的技能。
 */
@Tag(name = "技能管理", description = "Agent 技能的查询与详情展示")
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Resource
    private SkillRegistry skillRegistry;

    @Operation(summary = "列出所有技能", description = "返回技能名称和描述（不含正文）")
    @GetMapping
    public List<SkillSummary> list() {
        return skillRegistry.all().stream()
                .map(s -> new SkillSummary(s.name(), s.description()))
                .toList();
    }

    @Operation(summary = "获取技能详情", description = "返回单个技能的完整 SKILL.md 正文")
    @GetMapping("/{name}")
    public ResponseEntity<Skill> get(@PathVariable String name) {
        return skillRegistry.findByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record SkillSummary(String name, String description) {}
}
