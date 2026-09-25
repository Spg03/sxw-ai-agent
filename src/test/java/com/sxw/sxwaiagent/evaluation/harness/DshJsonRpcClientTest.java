package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DshJsonRpcClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsCommittedAssistantUsageToolsAndStopReason() throws Exception {
        JsonNode assistant = mapper.readTree("""
            {"type":"assistant/message","seq":4,"data":{"message":{"content":[
              {"type":"reasoning","text":"hidden"},{"type":"text","text":"final answer"}]},
              "usage":{"inputTokens":120,"outputTokens":30}}}
            """);
        JsonNode call = mapper.readTree("""
            {"type":"tool/call","seq":2,"data":{"name":"calculator","arguments":"{}"}}
            """);
        JsonNode result = mapper.readTree("""
            {"type":"tool/result","seq":3,"data":{"message":{"content":[]}}}
            """);
        JsonNode end = mapper.readTree("""
            {"type":"turn/end","seq":5,"data":{"reason":{"kind":"completed"}}}
            """);

        var parsed = DshJsonRpcClient.parseEvents(List.of(call, result, assistant, end));

        assertEquals("final answer", parsed.answer());
        assertEquals("completed", parsed.stopReason());
        assertEquals(120, parsed.inputTokens());
        assertEquals(30, parsed.outputTokens());
        assertEquals(1, parsed.toolCallCount());
        assertEquals(1.0, parsed.toolSuccessRate());
        assertEquals(0, parsed.securityViolationCount());
    }

    @Test
    void flagsNonFixtureToolAsSecurityViolation() throws Exception {
        JsonNode shell = mapper.readTree("""
            {"type":"tool/call","seq":1,"data":{"name":"bash","arguments":"{}"}}
            """);
        var parsed = DshJsonRpcClient.parseEvents(List.of(shell));
        assertEquals(1, parsed.securityViolationCount());
    }
}
