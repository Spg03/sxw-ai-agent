package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceRun;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
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

@RestController
@RequestMapping("/agent/traces")
@Validated
public class AgentTraceController {

    private final AgentTraceStore agentTraceStore;

    public AgentTraceController(AgentTraceStore agentTraceStore) {
        this.agentTraceStore = agentTraceStore;
    }

    @GetMapping
    public Result<List<AgentTraceRun>> listAllTraces(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(agentTraceStore.findAllRecent(limit));
    }

    @GetMapping("/{id}")
    public Result<List<AgentTraceRun>> recentRuns(
            @PathVariable @NotBlank @Size(max = 64) String id,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.ok(agentTraceStore.recentRuns(id, limit));
    }

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
