package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.note.repository.NoteEntryRepository;
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

import java.time.Instant;
import java.util.List;

@Tag(name = "仪表盘", description = "系统统计概览与健康检查")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TreeholeEntryRepository treeholeEntryRepository;
    private final NoteEntryRepository noteEntryRepository;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(
            TreeholeEntryRepository treeholeEntryRepository,
            NoteEntryRepository noteEntryRepository,
            EvalCaseRepository evalCaseRepository,
            EvalRunRepository evalRunRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.treeholeEntryRepository = treeholeEntryRepository;
        this.noteEntryRepository = noteEntryRepository;
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
    public Result<DashboardStats> getStats(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.ok(buildStats(user.userId()));
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
                SELECT c.conversation_id,
                       COALESCE((SELECT m.content FROM ai_conversation_message m
                                 WHERE m.conversation_id = c.conversation_id AND m.role = 'USER'
                                 ORDER BY m.sequence_no DESC LIMIT 1), c.title) AS content,
                       c.updated_at
                FROM ai_conversation c
                WHERE c.user_id = ?
                ORDER BY c.updated_at DESC
                LIMIT 4
                """, (rs, rowNum) -> new DashboardConversation(
                rs.getString("conversation_id"),
                readableTitle(rs.getString("content")),
                rs.getTimestamp("updated_at").toInstant()
        ), user.userId());

        long todayMessages = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_conversation_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE c.user_id = ? AND m.role = 'USER' AND m.created_at >= CURRENT_DATE
                """, Long.class, user.userId());
        int companionMinutes = (int) Math.min(180, todayMessages * 2);
        return Result.ok(new DashboardOverview(buildStats(user.userId()), activities, conversations,
                companionMinutes, Instant.now()));
    }

    private DashboardStats buildStats(Long userId) {
        DashboardStats stats = new DashboardStats();
        Long chatCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE user_id = ?", Long.class, userId);
        stats.setChatCount(chatCount != null ? chatCount : 0);
        stats.setTreeholeCount(treeholeEntryRepository.countByUserId(userId));
        stats.setNoteCount(noteEntryRepository.countByUserId(userId));

        stats.setEvalRunCount(evalRunRepository.count());
        stats.setEvalCaseCount(evalCaseRepository.countActive());
        return stats;
    }

    private static String readableTitle(String content) {
        if (content == null || content.isBlank()) return "新建对话";
        String value = content.replaceAll("\\s+", " ").trim();
        return value.length() > 80 ? value.substring(0, 80) + "…" : value;
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
