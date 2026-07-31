package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具审计日志
 * <p>
 * 记录所有工具调用的审计信息，用于追踪和分析工具使用情况。
 * 每次工具调用都会记录，无论成功或失败。
 * <p>
 * 底层存储已迁移到 PostgreSQL（ai_tool_audit_log 表），
 * 通过 ToolAuditLogRepository 进行持久化，重启不丢失。
 * 支持容量限制（保留最近 10000 条），防止表无限增长。
 */
@Component
public class ToolAuditLog {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditLog.class);

    /**
     * 每写入 TRIM_INTERVAL 条记录后触发一次容量检查
     */
    private static final int TRIM_INTERVAL = 100;

    private final ToolAuditLogRepository repository;

    /** 写入计数器，用于控制 trim 频率 */
    private final AtomicInteger writeCounter = new AtomicInteger(0);

    public ToolAuditLog(ToolAuditLogRepository repository) {
        this.repository = repository;
    }

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

        try {
            repository.insert(entry);

            // 每 TRIM_INTERVAL 次写入触发一次容量清理
            if (writeCounter.incrementAndGet() % TRIM_INTERVAL == 0) {
                repository.trimToCapacity();
            }
        } catch (Exception e) {
            log.warn("Failed to persist audit log: requestId={}, tool={}, error={}",
                    requestId, toolName, e.getMessage(), e);
        }

        log.debug("Audit logged: requestId={}, tool={}, status={}, latency={}ms",
                requestId, toolName, status, latencyMs);
    }

    /**
     * 获取指定请求的所有审计日志
     */
    public List<AuditEntry> getByRequestId(String requestId) {
        try {
            return repository.findByRequestId(requestId);
        } catch (Exception e) {
            log.warn("Failed to query audit log by requestId={}: {}", requestId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取指定追踪的所有审计日志
     */
    public List<AuditEntry> getByTraceId(String traceId) {
        try {
            return repository.findByTraceId(traceId);
        } catch (Exception e) {
            log.warn("Failed to query audit log by traceId={}: {}", traceId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取指定工具的所有审计日志
     */
    public List<AuditEntry> getByToolName(String toolName) {
        try {
            return repository.findByToolName(toolName);
        } catch (Exception e) {
            log.warn("Failed to query audit log by toolName={}: {}", toolName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 清理指定请求的审计日志
     */
    public void clear(String requestId) {
        try {
            repository.deleteByRequestId(requestId);
        } catch (Exception e) {
            log.warn("Failed to delete audit log for requestId={}: {}", requestId, e.getMessage());
        }
    }

    /**
     * 获取总审计条目数
     */
    public int size() {
        try {
            return repository.count();
        } catch (Exception e) {
            log.warn("Failed to count audit log: {}", e.getMessage());
            return 0;
        }
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
