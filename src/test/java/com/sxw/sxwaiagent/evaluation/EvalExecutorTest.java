package com.sxw.sxwaiagent.evaluation;

import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.dto.AgentResponse;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.runtime.AgentRuntime;
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

    @Mock AgentRuntime runtime;
    @Mock ObjectProvider<LlmJudgeEvaluator> judgeProvider;
    @Mock LlmJudgeEvaluator judge;
    @Mock AgentProfile profile;

    private EvalExecutor executor;

    private EvalCase makeCase(String expected, String judgeCriteria, ValidationMode mode) {
        return new EvalCase(null, "case-1", "Test", EvalCaseType.CONVERSATION,
            EvalCaseStatus.ACTIVE, "GENERAL",
            "input", expected, null,
            judgeCriteria, mode,
            null, 5, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private AgentResponse mockResponse(String answer) {
        return AgentResponse.builder()
            .requestId("req-1")
            .traceId("trace-1")
            .answer(answer)
            .citations(List.of())
            .toolCalls(List.of())
            .latencyMs(100)
            .build();
    }

    @BeforeEach
    void setup() {
        lenient().when(profile.code()).thenReturn(AgentProfileCode.GENERAL);
        executor = new EvalExecutor(List.of(profile), List.of(runtime), judgeProvider);
        executor.init();
    }

    @Test
    void keywordOnly_noJudgeCriteria_passes() {
        when(runtime.execute(any(AgentContext.class))).thenReturn(mockResponse("hello world"));
        EvalCase evalCase = makeCase("hello", null, ValidationMode.KEYWORD_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertTrue(result.passed());
        assertTrue(result.keywordPassed());
        assertNull(result.judgeStatus());
    }

    @Test
    void llmOnly_skipsKeywordCheck() {
        when(runtime.execute(any(AgentContext.class))).thenReturn(mockResponse("response"));
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
        when(runtime.execute(any(AgentContext.class))).thenReturn(mockResponse("hello"));
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
        when(runtime.execute(any(AgentContext.class))).thenReturn(mockResponse("xyz"));
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
        when(runtime.execute(any(AgentContext.class))).thenReturn(mockResponse("response"));
        when(judgeProvider.getIfAvailable()).thenReturn(null);

        EvalCase evalCase = makeCase(null, "criteria", ValidationMode.LLM_ONLY);

        EvalResult result = executor.execute(evalCase);

        assertFalse(result.passed());
        assertEquals(JudgeStatus.UNAVAILABLE, result.judgeStatus());
    }
}
