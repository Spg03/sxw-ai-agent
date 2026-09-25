package com.sxw.sxwaiagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.agent.dto.AgentContext;
import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.prompt.AssembledPrompt;
import com.sxw.sxwaiagent.context.TokenCounter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRunSnapshotServiceTest {
    @ParameterizedTest
    @EnumSource(AgentProfileCode.class)
    void migrationFitsFullSnapshotIdentifiersAndPreservesExistingValues(AgentProfileCode code) throws Exception {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        doReturn("run_test").when(mockJdbc).query(anyString(), any(ResultSetExtractor.class), eq("req"), eq("chat"));
        AtomicReference<Object[]> parameters = new AtomicReference<>();
        when(mockJdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            parameters.set((Object[]) invocation.getRawArguments()[1]);
            return 1;
        });
        AgentProfile profile = mock(AgentProfile.class);
        when(profile.code()).thenReturn(code);
        when(profile.enabledToolNames()).thenReturn(List.of("noteTool"));
        AgentContext context = AgentContext.builder().requestId("req").traceId("trace").chatId("chat")
                .profile(profile).userMessage("hello").metadata(Map.of()).build();
        String staticHash = "a".repeat(64);
        new AgentRunSnapshotService(mockJdbc, new ObjectMapper(), new TokenCounter()).capture("req", 1, context,
                new AssembledPrompt("policy", List.of(), staticHash, "b".repeat(64), "c".repeat(64)),
                List.of(new UserMessage("hello")), 10, Map.of());
        Object[] args = parameters.get();
        assertNotNull(args);
        assertEquals(71, ((String) args[8]).length());
        assertEquals(code.name() + ":" + staticHash, args[9]);

        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate database = new JdbcTemplate(dataSource);
        database.execute("CREATE TABLE ai_context_snapshot(tool_schema_version VARCHAR(64), "
                + "system_prompt_version VARCHAR(64), context_hash VARCHAR(80))");
        database.update("INSERT INTO ai_context_snapshot VALUES ('legacy-tool','legacy-prompt','legacy-context')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> database.update("INSERT INTO ai_context_snapshot VALUES (?,?,?)", args[8], args[9], args[11]));
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V17__context_snapshot_version_lengths.sql"));
        }
        database.update("INSERT INTO ai_context_snapshot VALUES (?,?,?)", args[8], args[9], args[11]);
        assertEquals(args[9], database.queryForObject("SELECT system_prompt_version FROM ai_context_snapshot "
                + "WHERE tool_schema_version=?", String.class, args[8]));
        assertEquals("legacy-prompt", database.queryForObject("SELECT system_prompt_version FROM ai_context_snapshot "
                + "WHERE tool_schema_version='legacy-tool'", String.class));
    }
}
