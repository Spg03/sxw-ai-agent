package com.sxw.sxwaiagent.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EvalService 单元测试
 * <p>
 * 覆盖 Case 管理、Run 生命周期、执行与异常路径。
 */
@ExtendWith(MockitoExtension.class)
class EvalServiceTest {

    @Mock private EvalCaseRepository evalCaseRepository;
    @Mock private EvalRunRepository evalRunRepository;
    @Mock private EvalResultRepository evalResultRepository;
    @Mock private EvalExecutor evalExecutor;

    private EvalService evalService;

    @BeforeEach
    void setUp() {
        evalService = new EvalService(evalCaseRepository, evalRunRepository, evalResultRepository, evalExecutor);
    }

    // ── Case Management ─────────────────────────────────────────────

    @Nested
    @DisplayName("Case 管理")
    class CaseManagement {

        @Test
        @DisplayName("createCase 生成唯一 caseId 并持久化")
        void createCaseGeneratesIdAndSaves() {
            EvalCase result = evalService.createCase(
                "测试用例", EvalCaseType.CONVERSATION, "GENERAL",
                "你好", "你好！", null, ValidationMode.KEYWORD_ONLY, "tester"
            );

            assertNotNull(result);
            assertTrue(result.caseId().startsWith("eval-"));
            assertEquals("测试用例", result.caseName());
            assertEquals(EvalCaseStatus.DRAFT, result.status());
            assertEquals(ValidationMode.KEYWORD_ONLY, result.validationMode());
            verify(evalCaseRepository, times(1)).save(any(EvalCase.class));
        }

        @Test
        @DisplayName("createCase validationMode 为 null 时默认 KEYWORD_ONLY")
        void createCaseDefaultsValidationMode() {
            EvalCase result = evalService.createCase(
                "默认模式", EvalCaseType.CONVERSATION, "LOVE",
                "input", "output", null, null, null
            );

            assertEquals(ValidationMode.KEYWORD_ONLY, result.validationMode());
        }

        @Test
        @DisplayName("activateCase 更新状态为 ACTIVE")
        void activateCaseUpdatesStatus() {
            evalService.activateCase("eval-123");
            verify(evalCaseRepository).updateStatus("eval-123", EvalCaseStatus.ACTIVE);
        }

        @Test
        @DisplayName("disableCase 更新状态为 DISABLED")
        void disableCaseUpdatesStatus() {
            evalService.disableCase("eval-456");
            verify(evalCaseRepository).updateStatus("eval-456", EvalCaseStatus.DISABLED);
        }

        @Test
        @DisplayName("findCase 存在时返回 Optional")
        void findCaseReturnsPresent() {
            EvalCase mockCase = new EvalCase("eval-1", "case", EvalCaseType.CONVERSATION,
                "GENERAL", "in", "out");
            when(evalCaseRepository.findByCaseId("eval-1")).thenReturn(Optional.of(mockCase));

            Optional<EvalCase> result = evalService.findCase("eval-1");
            assertTrue(result.isPresent());
            assertEquals("eval-1", result.get().caseId());
        }

        @Test
        @DisplayName("findCase 不存在时返回空")
        void findCaseReturnsEmpty() {
            when(evalCaseRepository.findByCaseId("nope")).thenReturn(Optional.empty());
            assertTrue(evalService.findCase("nope").isEmpty());
        }

        @Test
        @DisplayName("deleteCase 调用 repository 删除")
        void deleteCaseDelegates() {
            evalService.deleteCase("eval-del");
            verify(evalCaseRepository).deleteByCaseId("eval-del");
        }
    }

    // ── Run Management ──────────────────────────────────────────────

    @Nested
    @DisplayName("Run 生命周期")
    class RunLifecycle {

        @Test
        @DisplayName("createRun 生成 runId 并持久化")
        void createRunGeneratesIdAndSaves() {
            EvalRun run = evalService.createRun("测试运行", "GENERAL", List.of("c1", "c2"), "api");

            assertNotNull(run);
            assertTrue(run.runId().startsWith("run-"));
            assertEquals(EvalRunStatus.PENDING, run.status());
            assertEquals(2, run.totalCases());
            verify(evalRunRepository, times(1)).save(any(EvalRun.class));
        }

        @Test
        @DisplayName("executeRun run 不存在时抛 IllegalArgumentException")
        void executeRunNotFoundThrows() {
            when(evalRunRepository.findByRunId("bad")).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> evalService.executeRun("bad"));
        }

        @Test
        @DisplayName("executeRun run 非 PENDING 状态时抛 IllegalStateException")
        void executeRunWrongStatusThrows() {
            EvalRun completedRun = new EvalRun(null, "run-1", "done", EvalRunStatus.COMPLETED,
                "GENERAL", List.of("c1"), 1, 1, 0, 0, 100.0, 500L,
                "api", null, null, null, null);
            when(evalRunRepository.findByRunId("run-1")).thenReturn(Optional.of(completedRun));

            assertThrows(IllegalStateException.class, () -> evalService.executeRun("run-1"));
        }

        @Test
        @DisplayName("executeRun 正常执行：调用 executor、持久化结果、更新统计")
        void executeRunSuccessPath() {
            EvalRun pendingRun = new EvalRun("run-ok", "正常", "GENERAL", List.of("c1", "c2"), "api");
            when(evalRunRepository.findByRunId("run-ok")).thenReturn(Optional.of(pendingRun));

            EvalCase case1 = new EvalCase("c1", "case1", EvalCaseType.CONVERSATION, "GENERAL", "in1", "out1");
            EvalCase case2 = new EvalCase("c2", "case2", EvalCaseType.CONVERSATION, "GENERAL", "in2", "out2");
            when(evalCaseRepository.findByIds(List.of("c1", "c2"))).thenReturn(List.of(case1, case2));

            EvalResult pass = EvalResult.pass("c1", "case1", "actual", "out1", 100);
            EvalResult fail = EvalResult.fail("c2", "case2", "wrong", "out2", "mismatch", 200);
            when(evalExecutor.executeBatch(anyList())).thenReturn(List.of(pass, fail));

            evalService.executeRun("run-ok");

            verify(evalRunRepository).updateStatus("run-ok", EvalRunStatus.RUNNING);
            verify(evalResultRepository).saveBatch(eq("run-ok"), anyList());
            verify(evalRunRepository).updateResults(eq("run-ok"), eq(EvalRunStatus.COMPLETED),
                eq(1), eq(1), eq(0), eq(50.0), anyLong());
        }

        @Test
        @DisplayName("executeRun executor 异常时更新错误状态")
        void executeRunExceptionUpdatesError() {
            EvalRun pendingRun = new EvalRun("run-err", "异常", "GENERAL", List.of("c1"), "api");
            when(evalRunRepository.findByRunId("run-err")).thenReturn(Optional.of(pendingRun));

            EvalCase case1 = new EvalCase("c1", "case1", EvalCaseType.CONVERSATION, "GENERAL", "in", "out");
            when(evalCaseRepository.findByIds(List.of("c1"))).thenReturn(List.of(case1));
            when(evalExecutor.executeBatch(anyList())).thenThrow(new RuntimeException("LLM timeout"));

            evalService.executeRun("run-err");

            verify(evalRunRepository).updateError(eq("run-err"), contains("LLM timeout"));
        }

        @Test
        @DisplayName("executeRun 无可用 case 时记录错误")
        void executeRunNoCasesUpdatesError() {
            EvalRun pendingRun = new EvalRun("run-empty", "空", "GENERAL", List.of("c1"), "api");
            when(evalRunRepository.findByRunId("run-empty")).thenReturn(Optional.of(pendingRun));
            when(evalCaseRepository.findByIds(anyList())).thenReturn(List.of());

            evalService.executeRun("run-empty");

            verify(evalRunRepository).updateError(eq("run-empty"), contains("No active cases"));
        }
    }

    // ── Query ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("getRunResults 委托 repository")
        void getRunResultsDelegates() {
            List<EvalResult> mockResults = List.of(EvalResult.pass("c1", "n", "a", "e", 10));
            when(evalResultRepository.findByRunId("run-1")).thenReturn(mockResults);

            assertEquals(mockResults, evalService.getRunResults("run-1"));
        }

        @Test
        @DisplayName("findRecentRuns 传递 limit")
        void findRecentRunsPassesLimit() {
            when(evalRunRepository.findRecent(5)).thenReturn(List.of());
            evalService.findRecentRuns(5);
            verify(evalRunRepository).findRecent(5);
        }
    }
}
