package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicEvalToolsetTest {
    private final DeterministicEvalToolset tools = new DeterministicEvalToolset(new ObjectMapper());

    @Test
    void calculatorAndFixtureAreDeterministic() {
        assertEquals("14", tools.execute("calculator", "{\"expression\":\"2+3*4\"}").content());
        assertEquals("Java 21", tools.execute("fixture_lookup", "{\"key\":\"project.runtime\"}")
            .content().substring("Spring Boot 3.4.4 / ".length()));
    }

    @Test
    void schemasRespectStricterWhitelist() {
        var schemas = tools.schemas(List.of("calculator"));
        assertEquals(1, schemas.size());
        assertEquals("calculator", schemas.get(0).path("function").path("name").asText());
    }

    @Test
    void forcedFailureNeverCreatesSideEffect() {
        var result = tools.execute("forced_failure", "{\"reason\":\"test\"}");
        assertFalse(result.success());
        assertEquals("FORCED_FAILURE:test", result.content());
    }
}
