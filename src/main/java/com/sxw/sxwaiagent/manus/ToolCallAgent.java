package com.sxw.sxwaiagent.manus;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.sxw.sxwaiagent.manus.model.AgentState;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base agent class that handles tool calling, implementing think() and act().
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // Available tools
    private final ToolCallback[] availableTools;

    // Chat response containing tool call decisions (used by act)
    private ChatResponse toolCallChatResponse;

    // Tool execution manager
    private final ToolCallingManager toolCallingManager;

    // Disable Spring AI built-in tool calling; manage options and message context manually
    private final ChatOptions chatOptions;

    // Latest assistant plain-text output (final answer when no tool calls)
    private String lastAssistantText = "";

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // Disable Spring AI built-in tool calling; manage options and message context manually
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * Process current state and decide next action.
     *
     * @return true if action is needed, false if done
     */
    @Override
    public boolean think() {
        // 1. Append next-step prompt to message history
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2. Trim history to avoid token overflow
        trimHistoryIfNeeded();
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            long thinkStart = System.currentTimeMillis();
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            long thinkLatency = System.currentTimeMillis() - thinkStart;
            // Record response for act() phase
            this.toolCallChatResponse = chatResponse;
            // 3. Parse tool calls from assistant message
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            traceEvent("think", null, getNextStepPrompt(), result, "ok", thinkLatency);
            log.info(getName() + " thinks: " + result);
            log.info(getName() + " selected " + toolCallList.size() + " tool(s) to use");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("Tool: %s, Args: %s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            for (AssistantMessage.ToolCall toolCall : toolCallList) {
                traceEvent("tool_call", toolCall.name(), toolCall.arguments(), "", "planned", 0);
            }
            // No tools needed: save assistant message and finish
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                this.lastAssistantText = result == null ? "" : result;
                setState(AgentState.FINISHED);
                return false;
            } else {
                // Tools will be called; no need to record assistant message manually
                return true;
            }
        } catch (RuntimeException e) {
            traceEvent("think", null, getNextStepPrompt(), e.getMessage(), "error", 0);
            log.error(getName() + " think error: " + e.getMessage());
            getMessageList().add(new AssistantMessage("Error during processing: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Single step: think + act.
     * When no tool calls are needed, returns the assistant's final text
     * so the frontend can display a natural language answer instead of a placeholder.
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                return StrUtil.isNotBlank(lastAssistantText) ? lastAssistantText : "Think done - no action needed";
            }
            return act();
        } catch (RuntimeException e) {
            log.error("step failed", e);
            return "Step execution failed: " + e.getMessage();
        }
    }

    /**
     * Execute tool calls and handle results
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "No tools to execute";
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        long toolStart = System.currentTimeMillis();
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        long toolLatency = System.currentTimeMillis() - toolStart;
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "Tool " + response.name() + " result: " + response.responseData())
                .collect(Collectors.joining("\n"));
        toolResponseMessage.getResponses().forEach(response ->
                traceEvent("tool_result", response.name(), "", response.responseData(), "ok", toolLatency));
        log.info(results);
        return results;
    }
}
