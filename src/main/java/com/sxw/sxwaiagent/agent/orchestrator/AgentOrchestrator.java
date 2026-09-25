package com.sxw.sxwaiagent.agent.orchestrator;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentRequest;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import com.sxw.sxwaiagent.agent.runtime.LegacyReActRuntime;
import com.sxw.sxwaiagent.agent.runtime.ToolUseLoopRuntime;
import com.sxw.sxwaiagent.plan.AgentRunMode;
import com.sxw.sxwaiagent.conversation.ConversationContextService;
import com.sxw.sxwaiagent.conversation.ConversationEventService;
import com.sxw.sxwaiagent.memory.AgentRunSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 统一调度器
 * <p>
 * 负责接收 AgentRequest，选择对应的 Profile 和 Runtime，
 * 构建 AgentContext 并执行，返回 AgentResponse。
 * <p>
 * P2 集成：支持双运行时切换
 * - LOVE / HERMES: 使用 LegacyReActRuntime（保持现有行为）
 * - GENERAL: 使用 ToolUseLoopRuntime（新的 Tool-Use Loop）
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    
    private final Map<AgentProfileCode, AgentProfile> profileMap;
    private final Map<AgentProfileCode, AgentRuntime> runtimeMap;
    private final RequestGuard requestGuard;
    private final ConversationContextService conversationContext;
    private final ConversationEventService conversationEvents;
    private final AgentRunSnapshotService snapshots;
    
    // 保存两种 Runtime 的引用，支持动态切换
    private final AgentRuntime legacyRuntime;
    private final AgentRuntime toolUseLoopRuntime;
    
    public AgentOrchestrator(
            List<AgentProfile> profiles,
            List<AgentRuntime> runtimes,
            RequestGuard requestGuard,
            ConversationContextService conversationContext,
            ConversationEventService conversationEvents,
            AgentRunSnapshotService snapshots,
            @Value("${sxw.agent.conversation-v3-enabled:true}") boolean conversationV3Enabled
    ) {
        this.profileMap = new EnumMap<>(AgentProfileCode.class);
        for (AgentProfile profile : profiles) {
            this.profileMap.put(profile.code(), profile);
        }
        
        // 查找两种 Runtime
        this.legacyRuntime = findRuntime(runtimes, "LegacyReActRuntime");
        this.toolUseLoopRuntime = findRuntime(runtimes, "ToolUseLoopRuntime");
        
        // P2 集成：分配 Runtime
        // 使用 ConcurrentHashMap 保证 switchRuntime() 的线程安全性
        this.runtimeMap = new ConcurrentHashMap<>();
        
        // V3 uses the same governed runtime for every profile so PostgreSQL remains
        // the only active history source. Legacy remains available for rollback.
        if (conversationV3Enabled && toolUseLoopRuntime != null) {
            this.runtimeMap.put(AgentProfileCode.LOVE, toolUseLoopRuntime);
            this.runtimeMap.put(AgentProfileCode.HERMES, toolUseLoopRuntime);
            this.runtimeMap.put(AgentProfileCode.GENERAL, toolUseLoopRuntime);
            log.info("Conversation V3 enabled: assigned ToolUseLoopRuntime to all profiles");
        } else if (legacyRuntime != null) {
            this.runtimeMap.put(AgentProfileCode.LOVE, legacyRuntime);
            this.runtimeMap.put(AgentProfileCode.HERMES, legacyRuntime);
            log.info("Assigned LegacyReActRuntime to LOVE and HERMES profiles");
        }
        
        // Complete only missing assignments. Do not overwrite the V3 mapping above.
        if (!this.runtimeMap.containsKey(AgentProfileCode.GENERAL)) {
            if (toolUseLoopRuntime != null) {
                this.runtimeMap.put(AgentProfileCode.GENERAL, toolUseLoopRuntime);
                log.info("Assigned ToolUseLoopRuntime to GENERAL profile");
            } else if (legacyRuntime != null) {
                this.runtimeMap.put(AgentProfileCode.GENERAL, legacyRuntime);
                log.warn("ToolUseLoopRuntime not found, falling back to LegacyReActRuntime for GENERAL profile");
            }
        }
        
        this.requestGuard = requestGuard;
        this.conversationContext = conversationContext;
        this.conversationEvents = conversationEvents;
        this.snapshots = snapshots;
        
        log.info("AgentOrchestrator initialized with {} profiles, {} runtime assignments",
                profileMap.size(), runtimeMap.size());
    }
    
    /**
     * 处理同步请求
     */
    public AgentResponse handleRequest(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        // 1. 生成 requestId 和 traceId
        String requestId = request.generateRequestId();
        String traceId = requestGuard.generateTraceId();
        
        log.info("[{}] Handling request: profile={}, chatId={}, stream={}",
                requestId, request.profile(), request.chatId(), request.stream());
        
        // 2. 获取 Profile
        AgentProfile profile = profileMap.get(request.profile());
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile: " + request.profile());
        }
        
        // 3. 获取 Runtime
        AgentRuntime runtime = runtimeMap.get(request.profile());
        if (runtime == null) {
            throw new IllegalStateException("No runtime configured for profile: " + request.profile());
        }
        
        log.info("[{}] Using runtime: {} for profile: {}", 
                requestId, runtime.getClass().getSimpleName(), request.profile());
        
        // 4. 从 PostgreSQL 事件流加载版本化上下文
        Map<String, Object> metadata = new HashMap<>(request.metadata() == null ? Map.of() : request.metadata());
        List<Message> history = loadHistory(request.chatId(), requestId, request.message(), metadata);
        
        // 5. 构建 AgentContext
        AgentRunMode runMode = AgentRunMode.CHAT;
        Object modeValue = metadata.get("runMode");
        if (modeValue != null) {
            try { runMode = AgentRunMode.valueOf(String.valueOf(modeValue)); } catch (IllegalArgumentException ignored) { }
        }
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .chatId(request.chatId())
                .profile(profile)
                .userMessage(request.message())
                .history(history)
                .metadata(metadata)
                .runMode(runMode)
                .planId((String) metadata.get("planId"))
                .build();
        
        // 6. Persist equivalent input before calling the model.
        String runId = metadata.get("userId") instanceof Number user
                ? snapshots.begin(request.chatId(), user.longValue(), requestId, traceId) : null;
        AgentResponse response;
        try {
            response = runtime.execute(context);
            if (metadata.get("userId") instanceof Number
                    && conversationEvents.requestState(request.chatId(), requestId).isPresent()) {
                conversationEvents.completeTurn(request.chatId(), requestId, response.answer(),
                        Map.of("traceId", traceId, "latencyMs", response.latencyMs()));
            }
            if (runId != null) snapshots.complete(runId, "COMPLETED");
        } catch (RuntimeException e) {
            if (metadata.get("userId") instanceof Number
                    && conversationEvents.requestState(request.chatId(), requestId).isPresent()) {
                conversationEvents.failTurn(request.chatId(), requestId,
                        e instanceof com.sxw.sxwaiagent.context.ContextBudgetExceededException
                                ? "REJECTED_TOO_LONG" : "FAILED",
                        e.getClass().getSimpleName());
            }
            if (runId != null) snapshots.complete(runId, "FAILED", e.getClass().getSimpleName());
            throw e;
        }
        
        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[{}] Request completed in {}ms using {}", requestId, latencyMs, runtime.getClass().getSimpleName());
        
        return response;
    }
    
    /**
     * 切换 Profile 的 Runtime
     * <p>
     * 支持动态切换，用于测试和灰度发布。
     * 
     * @param profile Profile 编码
     * @param runtimeType Runtime 类型（"legacy" 或 "tooluseloop"）
     */
    public void switchRuntime(AgentProfileCode profile, String runtimeType) {
        AgentRuntime runtime = switch (runtimeType.toLowerCase()) {
            case "legacy", "legacyreactruntime" -> legacyRuntime;
            case "tooluseloop", "tooluseloopruntime" -> toolUseLoopRuntime;
            default -> throw new IllegalArgumentException("Unknown runtime type: " + runtimeType);
        };
        
        if (runtime == null) {
            throw new IllegalStateException("Runtime not available: " + runtimeType);
        }
        
        runtimeMap.put(profile, runtime);
        log.info("Switched runtime for profile {} to {}", profile, runtime.getClass().getSimpleName());
    }
    
    /**
     * 获取已注册的 Profile 列表
     */
    public Set<AgentProfileCode> getRegisteredProfiles() {
        return Collections.unmodifiableSet(profileMap.keySet());
    }
    
    /**
     * 获取当前 Runtime 分配情况
     */
    public Map<AgentProfileCode, String> getRuntimeAssignments() {
        Map<AgentProfileCode, String> assignments = new HashMap<>();
        for (Map.Entry<AgentProfileCode, AgentRuntime> entry : runtimeMap.entrySet()) {
            assignments.put(entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
        return Collections.unmodifiableMap(assignments);
    }
    
    /**
     * 从 PostgreSQL 事件流加载摘要、工作记忆和最近完整 Turn。
     * <p>
     * chatId 为空或首次对话时返回空列表，不影响正常流程。
     */
    private List<Message> loadHistory(String chatId, String requestId, String currentMessage,
                                      Map<String,Object> metadata) {
        if (chatId == null || chatId.isBlank()) {
            log.debug("[{}] No chatId provided, skipping history load", requestId);
            return List.of();
        }
        try {
            if (metadata.get("userId") instanceof Number userId) {
                var slice = conversationContext.prepare(userId.longValue(), chatId, requestId, currentMessage);
                slice.contributeTo(metadata);
                return slice.messages();
            }
            return List.of();
        } catch (Exception e) {
            if (conversationEvents.requestState(chatId, requestId).isPresent()) {
                conversationEvents.failTurn(chatId, requestId, "FAILED", "CONTEXT_LOAD_FAILED");
            }
            throw new IllegalStateException("Unable to build conversation context", e);
        }
    }
    
    private AgentRuntime findRuntime(List<AgentRuntime> runtimes, String name) {
        for (AgentRuntime runtime : runtimes) {
            if (runtime.getClass().getSimpleName().equals(name)) {
                return runtime;
            }
        }
        log.warn("Runtime not found: {}", name);
        return null;
    }
}
