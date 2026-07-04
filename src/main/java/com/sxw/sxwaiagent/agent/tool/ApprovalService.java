package com.sxw.sxwaiagent.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批服务
 * <p>
 * 处理高风险工具的审批请求。当工具需要审批时，
 * 记录审批状态，支持查询和更新审批结果。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    // 存储待审批的工具调用请求
    private final Map<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();

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
                System.currentTimeMillis()
        );

        pendingApprovals.put(approvalId, request);
        log.info("Created approval request: {} for tool {}", approvalId, toolName);

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

        ApprovalRequest approved = new ApprovalRequest(
                request.approvalId(),
                request.requestId(),
                request.traceId(),
                request.toolName(),
                request.arguments(),
                request.reason(),
                ApprovalStatus.APPROVED,
                request.createdAt()
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

        ApprovalRequest rejected = new ApprovalRequest(
                request.approvalId(),
                request.requestId(),
                request.traceId(),
                request.toolName(),
                request.arguments(),
                request.reason(),
                ApprovalStatus.REJECTED,
                request.createdAt()
        );

        pendingApprovals.put(approvalId, rejected);
        log.info("Approval request {} rejected by {}: {}", approvalId, rejectedBy, reason);

        return true;
    }

    /**
     * 查询审批请求
     */
    public ApprovalRequest getApprovalRequest(String approvalId) {
        return pendingApprovals.get(approvalId);
    }

    /**
     * 检查审批是否已批准
     */
    public boolean isApproved(String approvalId) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        return request != null && request.status() == ApprovalStatus.APPROVED;
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
            long createdAt
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
