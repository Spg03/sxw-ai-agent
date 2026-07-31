package com.sxw.sxwaiagent.web.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }
}
