package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一工具执行器
 * <p>
 * 负责执行工具调用，并记录追踪信息。
 */
@Component
public class ToolExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    
    private final ToolCallback[] allTools;
    private final TraceRecorder traceRecorder;
    private final Map<String, ToolCallback> toolMap;
    
    public ToolExecutor(ToolCallback[] allTools, TraceRecorder traceRecorder) {
        this.allTools = allTools;
        this.traceRecorder = traceRecorder;
        this.toolMap = new HashMap<>();
        
        // 构建工具映射
        for (ToolCallback tool : allTools) {
            String toolName = tool.getToolDefinition().name();
            toolMap.put(toolName, tool);
        }
        
        log.info("ToolExecutor initialized with {} tools", toolMap.size());
    }
    
    /**
     * 执行工具
     *
     * @param toolName   工具名称
     * @param arguments  工具参数（JSON 字符串）
     * @param requestId  请求 ID
     * @param traceId    追踪 ID
     * @param turn       轮次
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, String arguments, String requestId, String traceId, int turn) {
        long startTime = System.currentTimeMillis();
        
        log.info("[{}] Executing tool: {} (turn {})", requestId, toolName, turn);
        
        ToolCallback tool = toolMap.get(toolName);
        if (tool == null) {
            String errorMsg = "Tool not found: " + toolName;
            log.warn("[{}] {}", requestId, errorMsg);
            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, errorMsg, "failed", 0);
            return new ToolResult(errorMsg, false);
        }
        
        try {
            // 执行工具
            String result = tool.call(arguments);
            long latencyMs = System.currentTimeMillis() - startTime;
            
            log.info("[{}] Tool {} completed in {}ms", requestId, toolName, latencyMs);
            
            // 记录追踪
            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, result, "success", latencyMs);
            
            return new ToolResult(result, true);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            String errorMsg = "Tool execution failed: " + e.getMessage();
            log.error("[{}] Tool {} failed: {}", requestId, toolName, errorMsg, e);
            
            // 记录追踪
            traceRecorder.recordToolCall(requestId, traceId, turn, toolName, arguments, errorMsg, "failed", latencyMs);
            
            return new ToolResult(errorMsg, false);
        }
    }
    
    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        return toolMap.containsKey(toolName);
    }
}
