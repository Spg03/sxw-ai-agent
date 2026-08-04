package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.GlobalExceptionHandler;
import com.sxw.sxwaiagent.hermes.CandidateType;
import com.sxw.sxwaiagent.hermes.HermesCandidate;
import com.sxw.sxwaiagent.hermes.HermesCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HermesController Web 层契约测试（不启动完整 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class HermesControllerTest {

    @Mock
    private HermesCandidateService candidateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HermesController controller = new HermesController(candidateService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/hermes/candidates - 返回待审核列表")
    void getPendingCandidates_returnsList() throws Exception {
        when(candidateService.getPendingCandidates()).thenReturn(List.of(
                buildCandidate("c-1", HermesCandidate.CandidateStatus.PENDING),
                buildCandidate("c-2", HermesCandidate.CandidateStatus.PENDING)
        ));

        mockMvc.perform(get("/api/hermes/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].candidateId").value("c-1"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/hermes/candidates/{id} - 返回单个候选详情")
    void getCandidate_found() throws Exception {
        when(candidateService.getCandidate("c-1"))
                .thenReturn(Optional.of(buildCandidate("c-1", HermesCandidate.CandidateStatus.PENDING)));

        mockMvc.perform(get("/api/hermes/candidates/c-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.candidateId").value("c-1"))
                .andExpect(jsonPath("$.data.title").value("测试候选"));
    }

    @Test
    @DisplayName("GET /api/hermes/candidates/{id} - 候选不存在返回错误")
    void getCandidate_notFound() throws Exception {
        when(candidateService.getCandidate("nonexist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/hermes/candidates/nonexist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Candidate not found"));
    }

    @Test
    @DisplayName("POST /api/hermes/candidates/{id}/approve - 审批成功")
    void approveCandidate_success() throws Exception {
        when(candidateService.approveCandidate("c-1", "admin")).thenReturn(true);

        mockMvc.perform(post("/api/hermes/candidates/c-1/approve")
                        .param("reviewedBy", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("Candidate approved successfully"));
    }

    @Test
    @DisplayName("POST /api/hermes/candidates/{id}/approve - 审批失败")
    void approveCandidate_failure() throws Exception {
        when(candidateService.approveCandidate("c-1", "admin")).thenReturn(false);

        mockMvc.perform(post("/api/hermes/candidates/c-1/approve")
                        .param("reviewedBy", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("POST /api/hermes/candidates/{id}/reject - 拒绝成功")
    void rejectCandidate_success() throws Exception {
        when(candidateService.rejectCandidate("c-2", "reviewer")).thenReturn(true);

        mockMvc.perform(post("/api/hermes/candidates/c-2/reject")
                        .param("reviewedBy", "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("Candidate rejected successfully"));
    }

    @Test
    @DisplayName("POST /api/hermes/candidates/{id}/retry - 重试成功")
    void retryApply_success() throws Exception {
        when(candidateService.retryApply("c-3", "admin")).thenReturn(true);

        mockMvc.perform(post("/api/hermes/candidates/c-3/retry")
                        .param("reviewedBy", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("Candidate applied successfully on retry"));
    }

    @Test
    @DisplayName("GET /api/hermes/candidates/failed - 返回失败列表")
    void getFailedCandidates_returnsList() throws Exception {
        when(candidateService.getFailedCandidates()).thenReturn(List.of(
                buildCandidate("c-4", HermesCandidate.CandidateStatus.APPLY_FAILED)
        ));

        mockMvc.perform(get("/api/hermes/candidates/failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPLY_FAILED"));
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
