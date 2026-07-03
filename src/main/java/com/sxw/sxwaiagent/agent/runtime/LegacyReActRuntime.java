package com.sxw.sxwaiagent.agent.runtime;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.infrastructure.memory.ManusMemoryStore;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import com.sxw.sxwaiagent.love.LoveApp;
import com.sxw.sxwaiagent.manus.SxwManus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Legacy ReAct 运行时
 * <p>
 * 包装现有的 LoveApp、SxwManus、HermesAgent，提供统一的 AgentRuntime 接口。
 * P2 阶段将逐步替换为 ToolUseLoopRuntime。
 */
@Component
public class LegacyReActRuntime implements AgentRuntime {
    
    private static final Logger log = LoggerFactory.getLogger(LegacyReActRuntime.class);
    
    private final LoveApp loveApp;
    private final HermesAgent hermesAgent;
    private final ChatModel dashscopeChatModel;
    private final ToolCallback[] allTools;
    private final SkillRegistry skillRegistry;
    private final ManusMemoryStore manusMemoryStore;
    private final AgentTraceStore agentTraceStore;
    
    public LegacyReActRuntime(
            LoveApp loveApp,
            HermesAgent hermesAgent,
            ChatModel dashscopeChatModel,
            ToolCallback[] allTools,
            SkillRegistry skillRegistry,
            ManusMemoryStore manusMemoryStore,
            AgentTraceStore agentTraceStore
    ) {
        this.loveApp = loveApp;
        this.hermesAgent = hermesAgent;
        this.dashscopeChatModel = dashscopeChatModel;
        this.allTools = allTools;
        this.skillRegistry = skillRegistry;
        this.manusMemoryStore = manusMemoryStore;
        this.agentTraceStore = agentTraceStore;
    }
    
    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();
        
        AgentProfileCode profileCode = context.profile().code();
        log.info("[{}] LegacyReActRuntime executing for profile={}", context.requestId(), profileCode);
        
        String answer;
        List<String> citations = List.of();
        List<AgentResponse.ToolCallInfo> toolCalls = List.of();
        
        try {
            answer = switch (profileCode) {
                case LOVE -> executeLoveProfile(context);
                case GENERAL -> executeGeneralProfile(context);
                case HERMES -> executeHermesProfile(context);
            };
        } catch (Exception e) {
            log.error("[{}] Execution failed: {}", context.requestId(), e.getMessage(), e);
            answer = "抱歉，处理您的请求时遇到了问题，请稍后重试。";
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        return AgentResponse.builder()
                .requestId(context.requestId())
                .traceId(context.traceId())
                .answer(answer)
                .citations(citations)
                .toolCalls(toolCalls)
                .latencyMs(latencyMs)
                .build();
    }
    
    /**
     * 执行 LoveProfile（委托给 LoveApp）
     */
    private String executeLoveProfile(AgentContext context) {
        return loveApp.doChat(context.userMessage(), context.chatId());
    }
    
    /**
     * 执行 GeneralProfile（委托给 SxwManus）
     * <p>
     * 注意：SxwManus 不是线程安全的，每次请求必须 new 一个新实例。
     */
    private String executeGeneralProfile(AgentContext context) {
        // 创建新实例（非线程安全）
        SxwManus sxwManus = new SxwManus(allTools, dashscopeChatModel, skillRegistry);
        
        // 启用追踪
        if (agentTraceStore != null) {
            sxwManus.enableTracing(agentTraceStore, context.chatId());
        }
        
        // 加载历史记忆
        List<Message> history = manusMemoryStore.load(context.chatId());
        if (!history.isEmpty()) {
            sxwManus.getMessageList().addAll(history);
        }
        
        // 设置完成回调（保存记忆）
        sxwManus.setOnFinished(() -> manusMemoryStore.save(context.chatId(), sxwManus.getMessageList()));
        
        // 同步执行
        return sxwManus.run(context.userMessage());
    }
    
    /**
     * 执行 HermesProfile（委托给 HermesAgent）
     */
    private String executeHermesProfile(AgentContext context) {
        HermesReply reply = hermesAgent.comfort(context.userMessage(), context.chatId());
        
        // 将结构化回复转换为文本
        return String.format("""
                %s
                
                ---
                💭 情感标签: %s
                💡 建议: %s
                """, reply.reply(), reply.emotionTag(), reply.suggestion());
    }
}
