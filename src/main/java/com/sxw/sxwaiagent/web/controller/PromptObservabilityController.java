package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.prompt.PromptObservabilityService;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.conversation.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Prompt 可观测性", description = "当前用户会话的 Prompt 版本与脱敏 Context 元数据")
@RestController
@RequestMapping("/api/conversations/{conversationId}/observability")
@Validated
public class PromptObservabilityController {
    private final ConversationService conversations;
    private final PromptObservabilityService observability;

    public PromptObservabilityController(ConversationService conversations,
                                         PromptObservabilityService observability) {
        this.conversations = conversations;
        this.observability = observability;
    }

    @Operation(summary = "查询会话 Prompt 调用")
    @GetMapping("/prompt-runs")
    public Result<List<PromptObservabilityService.PromptRunView>> list(
            Authentication authentication, @PathVariable String conversationId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        long userId = user(authentication);
        conversations.get(userId, conversationId);
        return Result.ok(observability.list(userId, conversationId, limit));
    }

    @Operation(summary = "查询单次 Prompt 的分区元数据", description = "不返回完整 Prompt 或用户内容")
    @GetMapping("/prompt-runs/{requestId}/{callNo}")
    public Result<PromptObservabilityService.PromptCallDetail> detail(
            Authentication authentication, @PathVariable String conversationId,
            @PathVariable String requestId, @PathVariable @Min(1) int callNo) {
        long userId = user(authentication);
        conversations.get(userId, conversationId);
        return Result.ok(observability.detail(userId, conversationId, requestId, callNo));
    }

    private static long user(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
