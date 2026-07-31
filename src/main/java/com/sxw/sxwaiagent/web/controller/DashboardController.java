package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
import com.sxw.sxwaiagent.treehole.repository.TreeholeEntryRepository;
import com.sxw.sxwaiagent.evaluation.EvalCaseRepository;
import com.sxw.sxwaiagent.evaluation.EvalRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "仪表盘", description = "系统统计概览与健康检查")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TreeholeEntryRepository treeholeEntryRepository;
    private final NoteSkill noteSkill;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;

    public DashboardController(
            TreeholeEntryRepository treeholeEntryRepository,
            NoteSkill noteSkill,
            EvalCaseRepository evalCaseRepository,
            EvalRunRepository evalRunRepository
    ) {
        this.treeholeEntryRepository = treeholeEntryRepository;
        this.noteSkill = noteSkill;
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
    }

    @Data
    public static class DashboardStats {
        private long chatCount;
        private long treeholeCount;
        private long noteCount;
        private long evalRunCount;
        private long evalCaseCount;
    }

    @Operation(summary = "获取系统统计数据", description = "返回对话数、树洞数、笔记数、评测运行数等统计")
    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        DashboardStats stats = new DashboardStats();
        stats.setChatCount(0); // TODO: implement when conversation persistence is added
        stats.setTreeholeCount(treeholeEntryRepository.count());

        // listNotes() returns "ok: no notes" or "ok:\ntitle1\ntitle2..."
        String notes = noteSkill.listNotes();
        long noteCount = 0;
        if (notes != null && notes.startsWith("ok:\n")) {
            noteCount = notes.substring("ok:\n".length()).split("\n").length;
        }
        stats.setNoteCount(noteCount);

        stats.setEvalRunCount(evalRunRepository.count());
        stats.setEvalCaseCount(evalCaseRepository.countActive());
        return Result.ok(stats);
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> healthCheck() {
        return Result.ok("ok");
    }
}
