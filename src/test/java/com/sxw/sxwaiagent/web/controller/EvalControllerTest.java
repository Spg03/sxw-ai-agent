package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.evaluation.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvalControllerTest {
    @Test void submissionReturnsPendingWithoutInvokingModelInRequestThread() {
        var service=mock(EvalService.class);
        var run=new EvalRun("run","name","GENERAL",List.of("c"),"reviewer");
        when(service.createRun("name","GENERAL",List.of("c"),"reviewer")).thenReturn(run);
        var response=new EvalController(service).runEval(new EvalController.RunEvalRequest("name","GENERAL",List.of("c")),
                new UsernamePasswordAuthenticationToken("reviewer",""));
        assertEquals(run,response.data());
        verify(service,never()).executeRun(anyString());
    }
}
