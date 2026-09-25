package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.evaluation.harness.EvalHarnessProperties;
import com.sxw.sxwaiagent.evaluation.harness.HarnessCapabilities;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalAdapter;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalAdapterRegistry;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalOutput;
import com.sxw.sxwaiagent.evaluation.harness.HarnessEvalSession;
import com.sxw.sxwaiagent.evaluation.harness.HarnessTargetCode;
import com.sxw.sxwaiagent.infrastructure.eval.CaseResult;
import com.sxw.sxwaiagent.infrastructure.eval.LlmJudgeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalExecutorTest {

    @Mock HarnessEvalAdapter adapter;
    @Mock HarnessEvalSession session;
    @Mock ObjectProvider<LlmJudgeEvaluator> judgeProvider;
    @Mock LlmJudgeEvaluator judge;

    private EvalExecutor executor;

    private EvalCase makeCase(String expected, String judgeCriteria, ValidationMode mode) {
        return new EvalCase(null, "case-1", "Test", EvalCaseType.CONVERSATION,
            EvalCaseStatus.ACTIVE, "GENERAL",
            "input", expected, null,
            judgeCriteria, mode,
            null, 5, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private HarnessEvalOutput mockResponse(String answer) {
        return new HarnessEvalOutput(answer, "stop", 10, 5, 100,
            0, 1.0, 0, List.of(), null, null);
    }

    @BeforeEach
    void setup() {
        lenient().when(adapter.targetCode()).thenReturn(HarnessTargetCode.LOCAL);
        lenient().when(adapter.capabilities()).thenReturn(
            new HarnessCapabilities(true, true, true, java.util.Set.of(), null));
        lenient().when(adapter.openRun(anyString())).thenReturn(session);
        HarnessEvalAdapterRegistry registry = new HarnessEvalAdapterRegistry(List.of(adapter));
        EvalHarnessProperties properties = new EvalHarnessProperties(null, null, null, null, "key", null);
        executor = new EvalExecutor(registry, properties, judgeProvider);
    }

    @Test
    void keywordOnly_noJudgeCriteria_passes() {
        when(session.execute(any())).thenReturn(mockResponse("hello world"));
        EvalCase evalCase = makeCase("hello", null, ValidationMode.KEYWORD_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertTrue(result.keywordPassed());
        assertNull(result.judgeStatus());
    }

    @Test
    void llmOnly_skipsKeywordCheck() {
        when(session.execute(any())).thenReturn(mockResponse("response"));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new CaseResult.Check(true, 0.9, "good"));

        EvalCase evalCase = makeCase(null, "Is the response polite?", ValidationMode.LLM_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertEquals(JudgeStatus.PASSED, result.judgeStatus());
    }

    @Test
    void allMode_keywordPassJudgeFail_fails() {
        when(session.execute(any())).thenReturn(mockResponse("hello"));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new CaseResult.Check(false, 0.3, "bad"));

        EvalCase evalCase = makeCase("hello", "criteria", ValidationMode.ALL);

        EvalResult result = executor.execute(evalCase);

        assertFalse(result.passed());
        assertTrue(result.keywordPassed());
        assertEquals(JudgeStatus.FAILED, result.judgeStatus());
    }

    @Test
    void anyMode_keywordFailJudgePass_passes() {
        when(session.execute(any())).thenReturn(mockResponse("xyz"));
        when(judgeProvider.getIfAvailable()).thenReturn(judge);
        when(judge.evaluateCase(any(), anyString()))
            .thenReturn(new CaseResult.Check(true, 0.8, "ok"));

        EvalCase evalCase = makeCase("hello", "criteria", ValidationMode.ANY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertFalse(result.keywordPassed());
        assertEquals(JudgeStatus.PASSED, result.judgeStatus());
    }

    @Test
    void judgeUnavailable_failsWithUnavailableStatus() {
        when(session.execute(any())).thenReturn(mockResponse("response"));
        when(judgeProvider.getIfAvailable()).thenReturn(null);

        EvalCase evalCase = makeCase(null, "criteria", ValidationMode.LLM_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertFalse(result.passed());
        assertEquals(JudgeStatus.UNAVAILABLE, result.judgeStatus());
    }
}
