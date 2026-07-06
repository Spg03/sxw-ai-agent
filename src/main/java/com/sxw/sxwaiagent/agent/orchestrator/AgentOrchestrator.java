package com.sxw.sxwaiagent.agent.orchestrator;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentRequest;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
import com.sxw.sxwaiagent.agent.runtime.LegacyReActRuntime;
import com.sxw.sxwaiagent.agent.runtime.ToolUseLoopRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

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
    
    // 保存两种 Runtime 的引用，支持动态切换
    private final AgentRuntime legacyRuntime;
    private final AgentRuntime toolUseLoopRuntime;
    
    public AgentOrchestrator(
            List<AgentProfile> profiles,
            List<AgentRuntime> runtimes,
            RequestGuard requestGuard
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
        
        // LOVE 和 HERMES 继续使用 LegacyReActRuntime（保持现有行为）
        if (legacyRuntime != null) {
            this.runtimeMap.put(AgentProfileCode.LOVE, legacyRuntime);
            this.runtimeMap.put(AgentProfileCode.HERMES, legacyRuntime);
            log.info("Assigned LegacyReActRuntime to LOVE and HERMES profiles");
        }
        
        // GENERAL 使用 ToolUseLoopRuntime（P2 新特性）
        if (toolUseLoopRuntime != null) {
            this.runtimeMap.put(AgentProfileCode.GENERAL, toolUseLoopRuntime);
            log.info("Assigned ToolUseLoopRuntime to GENERAL profile");
        } else if (legacyRuntime != null) {
            // 降级：如果 ToolUseLoopRuntime 不可用，GENERAL 也使用 LegacyReActRuntime
            this.runtimeMap.put(AgentProfileCode.GENERAL, legacyRuntime);
            log.warn("ToolUseLoopRuntime not found, falling back to LegacyReActRuntime for GENERAL profile");
        }
        
        this.requestGuard = requestGuard;
        
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
        
        // 4. 构建 AgentContext
        AgentContext context = AgentContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .chatId(request.chatId())
                .profile(profile)
                .userMessage(request.message())
                .history(List.of()) // TODO: 从 ChatMemory 加载历史
                .metadata(request.metadata())
                .build();
        
        // 5. 执行
        AgentResponse response = runtime.execute(context);
        
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
