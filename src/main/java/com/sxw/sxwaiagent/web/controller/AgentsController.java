package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.classic.ClassicAgentService;
import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "经典 Agent", description = "传统工具型 Agent 和 Hermes 陡伴 Agent 接口")
@RestController
@RequestMapping("/agents")
public class AgentsController {

    private final ClassicAgentService classicAgentService;
    private final HermesAgent hermesAgent;

    public AgentsController(ClassicAgentService classicAgentService, HermesAgent hermesAgent) {
        this.classicAgentService = classicAgentService;
        this.hermesAgent = hermesAgent;
    }

    @Operation(summary = "获取可用 Agent 模式列表")
    @GetMapping("/modes")
    public Result<List<AgentMode>> modes() {
        return Result.ok(List.of(
                new AgentMode("classic", "ClassicAgent", "传统工具型 Agent，支持聊天、RAG、工具和 MCP"),
                new AgentMode("hermes", "Hermes", "私人树洞陪伴 Agent，支持情绪陪伴和总结")
        ));
    }

    @Operation(summary = "Classic Agent 对话", description = "传统工具型 Agent，支持聊天、RAG、工具和 MCP")
    @GetMapping("/classic/chat")
    public Result<String> classicChat(@RequestParam @NotBlank @Size(max = 2000) String message,
                                      @RequestParam @NotBlank @Size(max = 64) String chatId,
                                      @RequestParam(defaultValue = "chat") String mode) {
        return Result.ok(classicAgentService.chat(message, chatId, mode));
    }

    @Operation(summary = "Hermes 陡伴对话", description = "私人树洞陡伴 Agent，支持情绪陡伴和总结")
    @GetMapping("/hermes/chat")
    public Result<HermesReply> hermesChat(@RequestParam @NotBlank @Size(max = 2000) String message,
                                          @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return Result.ok(hermesAgent.comfort(message, chatId));
    }

    public record AgentMode(String id, String name, String description) {
    }
}
