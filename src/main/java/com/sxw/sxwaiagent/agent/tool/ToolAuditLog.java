package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具审计日志
 * <p>
 * 记录所有工具调用的审计信息，用于追踪和分析工具使用情况。
 * 每次工具调用都会记录，无论成功或失败。
 */
@Component
public class ToolAuditLog {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditLog.class);

    // 按 requestId 分组存储审计日志
    private final Map<String, List<AuditEntry>> auditLogByRequest = new ConcurrentHashMap<>();

    /**
     * 记录工具调用审计
     */
    public void record(
            String requestId,
            String traceId,
            int turn,
            String toolName,
            ToolRiskLevel riskLevel,
            String arguments,
            String result,
            String status,
            long latencyMs,
            boolean approved,
            String approvalId
    ) {
        AuditEntry entry = new AuditEntry(
                requestId,
                traceId,
                turn,
                toolName,
                riskLevel,
                arguments,
                result,
                status,
                latencyMs,
                approved,
                approvalId,
                System.currentTimeMillis()
        );

        auditLogByRequest.computeIfAbsent(requestId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);

        log.debug("Audit logged: requestId={}, tool={}, status={}, latency={}ms",
                requestId, toolName, status, latencyMs);
    }

    /**
     * 获取指定请求的所有审计日志
     */
    public List<AuditEntry> getByRequestId(String requestId) {
        List<AuditEntry> entries = auditLogByRequest.get(requestId);
        return entries != null ? new ArrayList<>(entries) : List.of();
    }

    /**
     * 获取指定追踪的所有审计日志
     */
    public List<AuditEntry> getByTraceId(String traceId) {
        return auditLogByRequest.values().stream()
                .flatMap(List::stream)
                .filter(entry -> traceId.equals(entry.traceId()))
                .toList();
    }

    /**
     * 获取指定工具的所有审计日志
     */
    public List<AuditEntry> getByToolName(String toolName) {
        return auditLogByRequest.values().stream()
                .flatMap(List::stream)
                .filter(entry -> toolName.equals(entry.toolName()))
                .toList();
    }

    /**
     * 清理指定请求的审计日志
     */
    public void clear(String requestId) {
        auditLogByRequest.remove(requestId);
    }

    /**
     * 获取总审计条目数
     */
    public int size() {
        return auditLogByRequest.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 审计日志条目
     */
    public record AuditEntry(
            String requestId,
            String traceId,
            int turn,
            String toolName,
            ToolRiskLevel riskLevel,
            String arguments,
            String result,
            String status,
            long latencyMs,
            boolean approved,
            String approvalId,
            long timestamp
    ) {
    }
}
