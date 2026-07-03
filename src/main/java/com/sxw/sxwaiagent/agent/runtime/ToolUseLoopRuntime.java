package com.sxw.sxwaiagent.agent.runtime;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.dto.AgentRunCompletedEvent;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.tool.ToolExecutor;
import com.sxw.sxwaiagent.agent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool-Use Loop 运行时
 * <p>
 * 基于 Claude Code 的 Tool-Use Loop 设计：
 * - 模型返回 tool_use 时，执行工具并继续循环
 * - 模型返回 end_turn 时，结束循环
 * - 减少 Thought 文本开销，专注于工具调用
 * <p>
 * 相比 ReAct（Think-Act 循环），Tool-Use Loop 更加直接：
 * - 不需要解析 "Thought: ..." 文本
 * - 不需要显式的 Action 标识
 * - 模型直接返回工具调用请求，运行时自动执行
 */
@Component
public class ToolUseLoopRuntime implements AgentRuntime {
    
    private static final Logger log = LoggerFactory.getLogger(ToolUseLoopRuntime.class);
    
    private static final int MAX_TURNS = 10;
    
    private final ChatModel chatModel;
    private final ToolExecutor toolExecutor;
    private final ApplicationEventPublisher eventPublisher;
    
    public ToolUseLoopRuntime(
            ChatModel chatModel,
            ToolExecutor toolExecutor,
            ApplicationEventPublisher eventPublisher
    ) {
        this.chatModel = chatModel;
        this.toolExecutor = toolExecutor;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();
        
        AgentProfile profile = context.profile();
        log.info("[{}] ToolUseLoopRuntime executing for profile={}", context.requestId(), profile.code());
        
        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        
        // 1. System Message（来自 Profile）
        if (profile.systemPrompt() != null && !profile.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(profile.systemPrompt()));
        }
        
        // 2. 历史消息
        if (context.history() != null) {
            messages.addAll(context.history());
        }
        
        // 3. 当前用户消息
        messages.add(new UserMessage(context.userMessage()));
        
        // 执行 Tool-Use Loop
        List<AgentResponse.ToolCallInfo> toolCalls = new ArrayList<>();
        String finalAnswer = "";
        int turn = 0;
        
        while (turn < MAX_TURNS) {
            turn++;
            log.info("[{}] Turn {}/{}", context.requestId(), turn, MAX_TURNS);
            
            // 调用模型
            Prompt prompt = new Prompt(messages);
            ChatResponse chatResponse = chatModel.call(prompt);
            
            if (chatResponse == null || chatResponse.getResult() == null) {
                log.warn("[{}] Model returned null response", context.requestId());
                break;
            }
            
            Generation generation = chatResponse.getResult();
            AssistantMessage assistantMessage = generation.getOutput();
            
            // 检查是否有工具调用
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                // 有工具调用，执行工具
                messages.add(assistantMessage);
                
                for (var toolCall : assistantMessage.getToolCalls()) {
                    log.info("[{}] Tool call: {}({})", context.requestId(), toolCall.name(), toolCall.arguments());
                    
                    // 检查工具是否在 Profile 允许列表中
                    if (!profile.enabledToolNames().contains(toolCall.name())) {
                        log.warn("[{}] Tool {} is not enabled for profile {}",
                                context.requestId(), toolCall.name(), profile.code());
                        messages.add(new UserMessage("Error: Tool " + toolCall.name() + " is not available for this profile."));
                        continue;
                    }
                    
                    // 执行工具
                    ToolResult toolResult = toolExecutor.execute(
                            toolCall.name(),
                            toolCall.arguments(),
                            context.requestId(),
                            context.traceId(),
                            turn
                    );
                    
                    // 记录工具调用信息
                    toolCalls.add(new AgentResponse.ToolCallInfo(
                            toolCall.name(),
                            toolCall.arguments(),
                            toolResult.content()
                    ));
                    
                    // 将工具结果作为 UserMessage 加入上下文（模拟 ToolResponseMessage）
                    messages.add(new UserMessage("Tool result for " + toolCall.name() + ": " + toolResult.content()));
                }
                
                // 继续循环
                continue;
            }
            
            // 没有工具调用，模型返回最终答案
            finalAnswer = assistantMessage.getText();
            log.info("[{}] Model returned final answer (turn {})", context.requestId(), turn);
            break;
        }
        
        if (turn >= MAX_TURNS) {
            log.warn("[{}] Reached max turns ({})", context.requestId(), MAX_TURNS);
            finalAnswer = "抱歉，我在处理您的请求时达到了最大轮次限制。请尝试简化您的请求。";
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        AgentResponse response = AgentResponse.builder()
                .requestId(context.requestId())
                .traceId(context.traceId())
                .answer(finalAnswer)
                .citations(List.of())
                .toolCalls(toolCalls)
                .latencyMs(latencyMs)
                .build();
        
        // 发布 AgentRunCompletedEvent
        eventPublisher.publishEvent(new AgentRunCompletedEvent(
                this,
                context.requestId(),
                context.traceId(),
                profile.code().name(),
                response
        ));
        
        return response;
    }
}
