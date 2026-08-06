package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
import com.sxw.sxwaiagent.treehole.repository.TreeholeEntryRepository;
import com.sxw.sxwaiagent.evaluation.EvalCaseRepository;
import com.sxw.sxwaiagent.evaluation.EvalRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Tag(name = "仪表盘", description = "系统统计概览与健康检查")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TreeholeEntryRepository treeholeEntryRepository;
    private final NoteSkill noteSkill;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(
            TreeholeEntryRepository treeholeEntryRepository,
            NoteSkill noteSkill,
            EvalCaseRepository evalCaseRepository,
            EvalRunRepository evalRunRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.treeholeEntryRepository = treeholeEntryRepository;
        this.noteSkill = noteSkill;
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        return Result.ok(buildStats());
    }

    @Operation(summary = "获取仪表盘概览", description = "返回统计指标、最近活动和最近对话，供工作台首页使用")
    @GetMapping("/overview")
    public Result<DashboardOverview> getOverview(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        List<DashboardActivity> activities = treeholeEntryRepository.findByUserIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .limit(4)
                .map(entry -> new DashboardActivity(
                        "treehole", entry.getTitle(), entry.getHermesSummary(), entry.getCreatedAt()))
                .toList();

        List<DashboardConversation> conversations = jdbcTemplate.query("""
                SELECT conversation_id, content, "timestamp"
                FROM (
                    SELECT DISTINCT ON (conversation_id) conversation_id, content, "timestamp"
                    FROM ai_chat_memory
                    WHERE type = 'USER'
                    ORDER BY conversation_id, "timestamp" DESC
                ) latest
                ORDER BY "timestamp" DESC
                LIMIT 4
                """, (rs, rowNum) -> new DashboardConversation(
                rs.getString("conversation_id"),
                extractText(rs.getString("content")),
                rs.getTimestamp("timestamp").toInstant()
        ));

        long todayMessages = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_chat_memory
                WHERE type = 'USER' AND "timestamp" >= CURRENT_DATE
                """, Long.class);
        int companionMinutes = (int) Math.min(180, todayMessages * 2);
        return Result.ok(new DashboardOverview(buildStats(), activities, conversations,
                companionMinutes, Instant.now()));
    }

    private DashboardStats buildStats() {
        DashboardStats stats = new DashboardStats();
        Long chatCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_memory WHERE type = 'USER'", Long.class);
        stats.setChatCount(chatCount != null ? chatCount : 0);
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
        return stats;
    }

    private static String extractText(String content) {
        if (content == null || content.isBlank()) return "新建对话";
        int start = content.indexOf("\"text\":");
        if (start < 0) return "新建对话";
        String value = content.substring(start + 7).trim();
        if (!value.startsWith("\"")) return "新建对话";
        int end = value.indexOf('"', 1);
        return (end > 1 ? value.substring(1, end) : "新建对话")
                .replace("\\n", " ").replace("\\\"", "\"");
    }

    public record DashboardActivity(String type, String title, String detail, Instant occurredAt) {}
    public record DashboardConversation(String id, String title, Instant occurredAt) {}
    public record DashboardOverview(
            DashboardStats stats,
            List<DashboardActivity> recentActivities,
            List<DashboardConversation> recentConversations,
            int companionMinutes,
            Instant generatedAt) {}

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> healthCheck() {
        return Result.ok("ok");
    }
}
