package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.dto.AgentRequest;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.orchestrator.AgentOrchestrator;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.common.api.Result;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 统一 Agent 控制器
 * <p>
 * 提供统一的 Agent 入口，支持通过 profile 参数选择不同的 Agent 模式。
 * 同时保留向后兼容的旧接口。
 */
@RestController
@RequestMapping("/api/agent")
@Validated
@Slf4j
public class AgentController {
    
    private final AgentOrchestrator agentOrchestrator;
    
    public AgentController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }
    
    /**
     * 统一对话入口
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @param profile Profile 编码（LOVE / GENERAL / HERMES）
     * @return Agent 响应
     */
    @GetMapping("/chat")
    public Result<AgentResponse> chat(
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64) String chatId,
            @NotNull AgentProfileCode profile
    ) {
        AgentRequest request = AgentRequest.builder()
                .chatId(chatId)
                .profile(profile)
                .message(message)
                .stream(false)
                .build();
        
        AgentResponse response = agentOrchestrator.handleRequest(request);
        return Result.ok(response);
    }
    
    /**
     * 统一对话入口（POST）
     */
    @PostMapping("/chat")
    public Result<AgentResponse> chatPost(@RequestBody @Validated AgentChatBody body) {
        AgentRequest request = AgentRequest.builder()
                .chatId(body.chatId())
                .profile(body.profile())
                .message(body.message())
                .stream(false)
                .build();
        
        AgentResponse response = agentOrchestrator.handleRequest(request);
        return Result.ok(response);
    }
    
    /**
     * 获取已注册的 Profile 列表
     */
    @GetMapping("/profiles")
    public Result<java.util.Set<AgentProfileCode>> profiles() {
        return Result.ok(agentOrchestrator.getRegisteredProfiles());
    }
    
    /**
     * 请求体
     */
    public record AgentChatBody(
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64) String chatId,
            @NotNull AgentProfileCode profile
    ) {}
}
