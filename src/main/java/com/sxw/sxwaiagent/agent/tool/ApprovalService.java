package com.sxw.sxwaiagent.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 审批服务
 * <p>
 * 处理高风险工具的审批请求。当工具需要审批时，
 * 记录审批状态，支持查询和更新审批结果。
 * <p>
 * 特性：
 * - PostgreSQL 持久化（ai_approval_request 表），重启不丢失
 * - TTL 自动过期（默认 24 小时），通过 expires_at 字段记录
 * - 定时清理任务（每小时执行），将过期的 PENDING 记录标记为 EXPIRED
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private final ApprovalRequestRepository repository;

    // 可配置的 TTL（毫秒）
    private long ttlMs = DEFAULT_TTL_MS;

    public ApprovalService(ApprovalRequestRepository repository) {
        this.repository = repository;
    }

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

        try {
            repository.insert(request);
        } catch (Exception e) {
            log.error("Failed to persist approval request: approvalId={}, tool={}, error={}",
                    approvalId, toolName, e.getMessage(), e);
            // 即使持久化失败也返回 request 对象，上层可据此处理
        }

        log.info("Created approval request: {} for tool {} (expires at {})",
                approvalId, toolName, request.expiresAt());

        return request;
    }

    /**
     * 批准审批请求
     */
    public boolean approve(String approvalId, String approvedBy, String comment) {
        Optional<ApprovalRequest> optRequest = repository.findByApprovalId(approvalId);
        if (optRequest.isEmpty()) {
            log.warn("Approval request not found: {}", approvalId);
            return false;
        }

        ApprovalRequest request = optRequest.get();

        if (request.status() != ApprovalStatus.PENDING) {
            log.warn("Approval request {} is not pending, current status: {}", approvalId, request.status());
            return false;
        }

        if (isExpired(request)) {
            log.warn("Approval request {} has expired", approvalId);
            repository.updateStatus(approvalId, ApprovalStatus.EXPIRED, null, null);
            return false;
        }

        try {
            int updated = repository.updateStatus(approvalId, ApprovalStatus.APPROVED, approvedBy, comment);
            if (updated == 0) {
                log.warn("Failed to update approval request: {}", approvalId);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to approve request: approvalId={}, error={}", approvalId, e.getMessage(), e);
            return false;
        }

        log.info("Approval request {} approved by {}: {}", approvalId, approvedBy, comment);
        return true;
    }

    /**
     * 拒绝审批请求
     */
    public boolean reject(String approvalId, String rejectedBy, String reason) {
        Optional<ApprovalRequest> optRequest = repository.findByApprovalId(approvalId);
        if (optRequest.isEmpty()) {
            log.warn("Approval request not found: {}", approvalId);
            return false;
        }

        ApprovalRequest request = optRequest.get();

        if (request.status() != ApprovalStatus.PENDING) {
            log.warn("Approval request {} is not pending, current status: {}", approvalId, request.status());
            return false;
        }

        if (isExpired(request)) {
            log.warn("Approval request {} has expired", approvalId);
            repository.updateStatus(approvalId, ApprovalStatus.EXPIRED, null, null);
            return false;
        }

        try {
            int updated = repository.updateStatus(approvalId, ApprovalStatus.REJECTED, rejectedBy, reason);
            if (updated == 0) {
                log.warn("Failed to update approval request: {}", approvalId);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to reject request: approvalId={}, error={}", approvalId, e.getMessage(), e);
            return false;
        }

        log.info("Approval request {} rejected by {}: {}", approvalId, rejectedBy, reason);
        return true;
    }

    /**
     * 查询审批请求
     */
    public ApprovalRequest getApprovalRequest(String approvalId) {
        Optional<ApprovalRequest> optRequest = repository.findByApprovalId(approvalId);
        if (optRequest.isEmpty()) {
            return null;
        }

        ApprovalRequest request = optRequest.get();
        if (isExpired(request)) {
            log.info("Marking expired approval request: {}", approvalId);
            repository.updateStatus(approvalId, ApprovalStatus.EXPIRED, null, null);
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
     * 将 expires_at < NOW() 且 status = PENDING 的记录批量更新为 EXPIRED
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredApprovals() {
        try {
            int expired = repository.expirePendingBefore(Instant.now());
            if (expired > 0) {
                log.info("Cleaned up {} expired approval requests", expired);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup expired approval requests: {}", e.getMessage(), e);
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
        REJECTED,
        /** 过期（由定时清理任务或查询时设置） */
        EXPIRED
    }
}
