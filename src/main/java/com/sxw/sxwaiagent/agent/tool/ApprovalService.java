package com.sxw.sxwaiagent.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批服务
 * <p>
 * 处理高风险工具的审批请求。当工具需要审批时，
 * 记录审批状态，支持查询和更新审批结果。
 * <p>
 * 特性：
 * - 内存存储（ConcurrentHashMap）
 * - TTL 自动过期清理（默认 24 小时）
 * - 定时清理任务（每小时执行）
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    // 存储待审批的工具调用请求
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();
    
    // 可配置的 TTL（毫秒）
    private long ttlMs = DEFAULT_TTL_MS;

    /**
     * 创建审批请求
     */
    public ApprovalRequest createApprovalRequest(
            String requestId,
            String traceId,
            String toolName,
            String arguments,
            String reason
    ) {
        String approvalId = "approval_" + System.currentTimeMillis() + "_" + requestId;

        ApprovalRequest request = new ApprovalRequest(
                approvalId,
                requestId,
                traceId,
                toolName,
                arguments,
                reason,
                ApprovalStatus.PENDING,
                System.currentTimeMillis(),
                System.currentTimeMillis() + ttlMs
        );

        pendingApprovals.put(approvalId, request);
        log.info("Created approval request: {} for tool {} (expires at {})", 
                approvalId, toolName, request.expiresAt());

        return request;
    }

    /**
     * 批准审批请求
     */
    public boolean approve(String approvalId, String approvedBy, String comment) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        if (request == null) {
            log.warn("Approval request not found: {}", approvalId);
            return false;
        }

        if (request.status() != ApprovalStatus.PENDING) {
            log.warn("Approval request {} is not pending, current status: {}", approvalId, request.status());
            return false;
        }

        if (isExpired(request)) {
            log.warn("Approval request {} has expired", approvalId);
            pendingApprovals.remove(approvalId);
            return false;
        }

        ApprovalRequest approved = new ApprovalRequest(
                request.approvalId(),
                request.requestId(),
                request.traceId(),
                request.toolName(),
                request.arguments(),
                request.reason(),
                ApprovalStatus.APPROVED,
                request.createdAt(),
                request.expiresAt()
        );

        pendingApprovals.put(approvalId, approved);
        log.info("Approval request {} approved by {}: {}", approvalId, approvedBy, comment);

        return true;
    }

    /**
     * 拒绝审批请求
     */
    public boolean reject(String approvalId, String rejectedBy, String reason) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        if (request == null) {
            log.warn("Approval request not found: {}", approvalId);
            return false;
        }

        if (request.status() != ApprovalStatus.PENDING) {
            log.warn("Approval request {} is not pending, current status: {}", approvalId, request.status());
            return false;
        }

        if (isExpired(request)) {
            log.warn("Approval request {} has expired", approvalId);
            pendingApprovals.remove(approvalId);
            return false;
        }

        ApprovalRequest rejected = new ApprovalRequest(
                request.approvalId(),
                request.requestId(),
                request.traceId(),
                request.toolName(),
                request.arguments(),
                request.reason(),
                ApprovalStatus.REJECTED,
                request.createdAt(),
                request.expiresAt()
        );

        pendingApprovals.put(approvalId, rejected);
        log.info("Approval request {} rejected by {}: {}", approvalId, rejectedBy, reason);

        return true;
    }

    /**
     * 查询审批请求
     */
    public ApprovalRequest getApprovalRequest(String approvalId) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        if (request != null && isExpired(request)) {
            log.info("Removing expired approval request: {}", approvalId);
            pendingApprovals.remove(approvalId);
            return null;
        }
        return request;
    }

    /**
     * 检查审批是否已批准
     */
    public boolean isApproved(String approvalId) {
        ApprovalRequest request = getApprovalRequest(approvalId);
        return request != null && request.status() == ApprovalStatus.APPROVED;
    }

    /**
     * 定时清理过期的审批请求（每小时执行）
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredApprovals() {
        int removed = 0;
        for (String approvalId : pendingApprovals.keySet()) {
            ApprovalRequest request = pendingApprovals.get(approvalId);
            if (request != null && isExpired(request)) {
                pendingApprovals.remove(approvalId);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired approval requests", removed);
        }
    }

    /**
     * 检查审批请求是否已过期
     */
    private boolean isExpired(ApprovalRequest request) {
        return request.expiresAt() > 0 && System.currentTimeMillis() > request.expiresAt();
    }

    /**
     * 设置 TTL（用于测试或配置）
     */
    public void setTtlMs(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * 获取当前 TTL
     */
    public long getTtlMs() {
        return ttlMs;
    }

    /**
     * 审批请求记录
     */
    public record ApprovalRequest(
            String approvalId,
            String requestId,
            String traceId,
            String toolName,
            String arguments,
            String reason,
            ApprovalStatus status,
            long createdAt,
            long expiresAt
    ) {
    }

    /**
     * 审批状态
     */
    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
