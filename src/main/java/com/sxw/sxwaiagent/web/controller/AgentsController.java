package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.classic.ClassicAgentService;
import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.common.api.Result;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agents")
public class AgentsController {

    private final ClassicAgentService classicAgentService;
    private final HermesAgent hermesAgent;

    public AgentsController(ClassicAgentService classicAgentService, HermesAgent hermesAgent) {
        this.classicAgentService = classicAgentService;
        this.hermesAgent = hermesAgent;
    }

    @GetMapping("/modes")
    public Result<List<AgentMode>> modes() {
        return Result.ok(List.of(
                new AgentMode("classic", "ClassicAgent", "传统工具型 Agent，支持聊天、RAG、工具和 MCP"),
                new AgentMode("hermes", "Hermes", "私人树洞陪伴 Agent，支持情绪陪伴和总结")
        ));
    }

    @GetMapping("/classic/chat")
    public Result<String> classicChat(@RequestParam @NotBlank @Size(max = 2000) String message,
                                      @RequestParam @NotBlank @Size(max = 64) String chatId,
                                      @RequestParam(defaultValue = "chat") String mode) {
        return Result.ok(classicAgentService.chat(message, chatId, mode));
    }

    @GetMapping("/hermes/chat")
    public Result<HermesReply> hermesChat(@RequestParam @NotBlank @Size(max = 2000) String message,
                                          @RequestParam @NotBlank @Size(max = 64) String chatId) {
        return Result.ok(hermesAgent.comfort(message, chatId));
    }

    public record AgentMode(String id, String name, String description) {
    }
}
