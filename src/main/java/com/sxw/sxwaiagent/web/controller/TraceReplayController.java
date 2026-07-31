package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.trace.TraceReplayService;
import com.sxw.sxwaiagent.trace.TraceRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "Trace 回放", description = "请求追踪记录的查询与回放")
@RestController
@RequestMapping("/api/traces")
@Validated
public class TraceReplayController {
    
    private static final Logger log = LoggerFactory.getLogger(TraceReplayController.class);
    
    private final TraceReplayService traceReplayService;
    
    public TraceReplayController(TraceReplayService traceReplayService) {
        this.traceReplayService = traceReplayService;
    }
    
    @Operation(summary = "获取最近追踪记录", description = "按时间倒序返回最近的请求追踪")
    @GetMapping
    public Result<List<TraceRecord>> getRecentTraces(
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        List<TraceRecord> traces = traceReplayService.getRecentTraces(limit);
        return Result.ok(traces);
    }
    
    @Operation(summary = "获取单条追踪详情")
    @GetMapping("/{traceId}")
    public Result<TraceRecord> getTrace(@PathVariable String traceId) {
        Optional<TraceRecord> trace = traceReplayService.getTrace(traceId);
        return trace.map(Result::ok).orElse(Result.error(404, "Trace not found"));
    }
    
    @Operation(summary = "按 Agent 类型查询追踪")
    @GetMapping("/agent/{agentType}")
    public Result<List<TraceRecord>> getTracesByAgentType(
        @PathVariable String agentType,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        List<TraceRecord> traces = traceReplayService.getTracesByAgentType(agentType, limit);
        return Result.ok(traces);
    }
    
    @Operation(summary = "回放追踪", description = "重新执行指定追踪记录的请求")
    @PostMapping("/{traceId}/replay")
    public Result<TraceReplayService.TraceReplayResult> replayTrace(@PathVariable String traceId) {
        log.info("Replaying trace: {}", traceId);
        
        TraceReplayService.TraceReplayResult result = traceReplayService.replay(traceId);
        
        if (result.success()) {
            return Result.ok(result);
        } else {
            return Result.error(500, result.message());
        }
    }
}
