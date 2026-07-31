package com.sxw.sxwaiagent.web.controller;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "健康检查", description = "应用存活探针")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "健康检查", description = "返回应用状态和时间戳")
    @GetMapping
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }
}
