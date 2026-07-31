package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceRun;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent 追踪", description = "Agent 执行追踪记录查询")
@RestController
@RequestMapping("/agent/traces")
@Validated
public class AgentTraceController {

    private final AgentTraceStore agentTraceStore;

    public AgentTraceController(AgentTraceStore agentTraceStore) {
        this.agentTraceStore = agentTraceStore;
    }

    @Operation(summary = "列出所有追踪记录", description = "按时间倒序返回最近的 Agent 执行追踪")
    @GetMapping
    public Result<List<AgentTraceRun>> listAllTraces(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(agentTraceStore.findAllRecent(limit));
    }

    @Operation(summary = "查询指定会话的追踪", description = "根据会话 ID 返回最近的执行记录")
    @GetMapping("/{id}")
    public Result<List<AgentTraceRun>> recentRuns(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.ok(agentTraceStore.recentRuns(id, limit));
    }

    @Operation(summary = "根据 traceId 查询", description = "精确查找单次执行的追踪详情")
    @GetMapping("/trace/{traceId}")
    public Result<AgentTraceRun> getByTraceId(
            @PathVariable @NotBlank @Size(max = 64) String traceId) {
        List<AgentTraceRun> runs = agentTraceStore.findByTraceId(traceId);
        if (runs.isEmpty()) {
            return Result.ok(null);
        }
        return Result.ok(runs.get(0));
    }
}
