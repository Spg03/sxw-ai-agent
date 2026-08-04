package com.sxw.sxwaiagent.hermes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HermesCandidateService 单元测试
 * <p>
 * 覆盖候选的创建、审批、拒绝、重试等核心业务流程。
 */
@ExtendWith(MockitoExtension.class)
class HermesCandidateServiceTest {

    @Mock
    private HermesCandidateRepository repository;

    @Mock
    private HermesApplier hermesApplier;

    private HermesCandidateService service;

    @BeforeEach
    void setUp() {
        service = new HermesCandidateService(repository, hermesApplier);
    }

    // ───────────────────── createCandidate ─────────────────────

    @Test
    @DisplayName("创建候选：生成 UUID 并保存到 Repository")
    void createCandidate_savesWithPendingStatus() {
        HermesCandidate result = service.createCandidate(
                "run-001", "chat-001", CandidateType.MEMORY,
                "用户偏好", "用户喜欢简洁回答", null
        );

        assertNotNull(result.candidateId());
        assertEquals("run-001", result.runId());
        assertEquals("chat-001", result.chatId());
        assertEquals(CandidateType.MEMORY, result.type());
        assertEquals("用户偏好", result.title());
        assertEquals(HermesCandidate.CandidateStatus.PENDING, result.status());
        assertNull(result.reviewedBy());
        assertNotNull(result.createdAt());

        verify(repository).save(any(HermesCandidate.class));
    }

    // ───────────────────── approveCandidate ─────────────────────

    @Test
    @DisplayName("审批通过：PENDING → APPROVED → APPLIED")
    void approveCandidate_success_transitionsToApplied() {
        HermesCandidate pending = buildCandidate("c-1", HermesCandidate.CandidateStatus.PENDING);
        when(repository.findById("c-1")).thenReturn(Optional.of(pending));
        when(hermesApplier.apply(any())).thenReturn("Memory created: mem-abc");

        boolean result = service.approveCandidate("c-1", "admin");

        assertTrue(result);
        verify(repository).updateStatus("c-1", HermesCandidate.CandidateStatus.APPROVED, "admin");
        verify(repository).updateStatus("c-1", HermesCandidate.CandidateStatus.APPLIED, "admin");
        verify(hermesApplier).apply(any());
    }

    @Test
    @DisplayName("审批通过但应用失败：状态变为 APPLY_FAILED")
    void approveCandidate_applyFails_transitionsToApplyFailed() {
        HermesCandidate pending = buildCandidate("c-2", HermesCandidate.CandidateStatus.PENDING);
        when(repository.findById("c-2")).thenReturn(Optional.of(pending));
        when(hermesApplier.apply(any())).thenThrow(new RuntimeException("DB error"));

        boolean result = service.approveCandidate("c-2", "admin");

        assertTrue(result);
        verify(repository).updateStatus("c-2", HermesCandidate.CandidateStatus.APPROVED, "admin");
        verify(repository).updateStatus("c-2", HermesCandidate.CandidateStatus.APPLY_FAILED, "admin");
    }

    @Test
    @DisplayName("审批不存在的候选：返回 false")
    void approveCandidate_notFound_returnsFalse() {
        when(repository.findById("nonexist")).thenReturn(Optional.empty());

        assertFalse(service.approveCandidate("nonexist", "admin"));
        verify(repository, never()).updateStatus(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("审批非 PENDING 状态候选：返回 false")
    void approveCandidate_notPending_returnsFalse() {
        HermesCandidate approved = buildCandidate("c-3", HermesCandidate.CandidateStatus.APPROVED);
        when(repository.findById("c-3")).thenReturn(Optional.of(approved));

        assertFalse(service.approveCandidate("c-3", "admin"));
        verify(repository, never()).updateStatus(anyString(), any(), anyString());
    }

    // ───────────────────── rejectCandidate ─────────────────────

    @Test
    @DisplayName("拒绝候选：PENDING → REJECTED")
    void rejectCandidate_success() {
        HermesCandidate pending = buildCandidate("c-4", HermesCandidate.CandidateStatus.PENDING);
        when(repository.findById("c-4")).thenReturn(Optional.of(pending));

        assertTrue(service.rejectCandidate("c-4", "reviewer"));
        verify(repository).updateStatus("c-4", HermesCandidate.CandidateStatus.REJECTED, "reviewer");
    }

    @Test
    @DisplayName("拒绝非 PENDING 候选：返回 false")
    void rejectCandidate_notPending_returnsFalse() {
        HermesCandidate applied = buildCandidate("c-5", HermesCandidate.CandidateStatus.APPLIED);
        when(repository.findById("c-5")).thenReturn(Optional.of(applied));

        assertFalse(service.rejectCandidate("c-5", "reviewer"));
    }

    // ───────────────────── retryApply ─────────────────────

    @Test
    @DisplayName("重试应用失败候选：APPLY_FAILED → APPLIED")
    void retryApply_success() {
        HermesCandidate failed = buildCandidate("c-6", HermesCandidate.CandidateStatus.APPLY_FAILED);
        when(repository.findById("c-6")).thenReturn(Optional.of(failed));
        when(hermesApplier.apply(failed)).thenReturn("Knowledge ingested");

        assertTrue(service.retryApply("c-6", "admin"));
        verify(repository).updateStatus("c-6", HermesCandidate.CandidateStatus.APPLIED, "admin");
    }

    @Test
    @DisplayName("重试仍然失败：返回 false")
    void retryApply_failsAgain_returnsFalse() {
        HermesCandidate failed = buildCandidate("c-7", HermesCandidate.CandidateStatus.APPLY_FAILED);
        when(repository.findById("c-7")).thenReturn(Optional.of(failed));
        when(hermesApplier.apply(failed)).thenThrow(new RuntimeException("timeout"));

        assertFalse(service.retryApply("c-7", "admin"));
        verify(repository, never()).updateStatus(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("重试非 APPLY_FAILED 候选：返回 false")
    void retryApply_wrongStatus_returnsFalse() {
        HermesCandidate pending = buildCandidate("c-8", HermesCandidate.CandidateStatus.PENDING);
        when(repository.findById("c-8")).thenReturn(Optional.of(pending));

        assertFalse(service.retryApply("c-8", "admin"));
    }

    // ───────────────────── query methods ─────────────────────

    @Test
    @DisplayName("获取待审核列表")
    void getPendingCandidates_delegatesToRepository() {
        List<HermesCandidate> expected = List.of(
                buildCandidate("c-9", HermesCandidate.CandidateStatus.PENDING)
        );
        when(repository.findByStatus(HermesCandidate.CandidateStatus.PENDING)).thenReturn(expected);

        assertEquals(expected, service.getPendingCandidates());
    }

    @Test
    @DisplayName("获取失败列表")
    void getFailedCandidates_delegatesToRepository() {
        List<HermesCandidate> expected = List.of(
                buildCandidate("c-10", HermesCandidate.CandidateStatus.APPLY_FAILED)
        );
        when(repository.findByStatus(HermesCandidate.CandidateStatus.APPLY_FAILED)).thenReturn(expected);

        assertEquals(expected, service.getFailedCandidates());
    }

    // ───────────────────── helpers ─────────────────────

    private HermesCandidate buildCandidate(String id, HermesCandidate.CandidateStatus status) {
        return new HermesCandidate(
                id, "run-test", "chat-test",
                CandidateType.MEMORY, "测试候选", "测试内容", null,
                status, null,
                Instant.now(), null,
                "run-test", new BigDecimal("0.85")
        );
    }
}
