package com.sxw.sxwaiagent.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.infrastructure.trace.DbAgentTraceRepository;
import com.sxw.sxwaiagent.attachment.*;
import com.sxw.sxwaiagent.evaluation.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.PessimisticLockingFailureException;
import com.sxw.sxwaiagent.auth.model.RefreshToken;
import com.sxw.sxwaiagent.auth.repository.RefreshTokenRepository;
import com.sxw.sxwaiagent.hermes.HermesCandidateRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Runs against a disposable CI database, never the application's configured datasource. */
@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL", matches=".+")
class PostgresMigrationTest {
    private PGSimpleDataSource ds;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach void setUp() {
        String url = System.getenv("TEST_POSTGRES_URL");
        if (!url.matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]+_test")) {
            throw new IllegalArgumentException("Use a dedicated database whose name ends with _test");
        }
        ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(System.getenv("TEST_POSTGRES_USER"));
        ds.setPassword(System.getenv("TEST_POSTGRES_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public");
        schema = "regression_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        ds.setCurrentSchema(schema + ",public");
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    @AfterEach void cleanup() {
        if (schema != null && schema.matches("regression_[a-f0-9]{32}")) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void freshDatabaseMigratesAndSupportsQueueAndOwnershipQueries() {
        assertTrue(flyway(null).migrate().migrationsExecuted >= 18);
        flyway(null).validate();
        jdbc.update("INSERT INTO sxw_users(id,username,password_hash,nickname) VALUES (101,'alice','test','Alice'),(102,'bob','test','Bob')");
        jdbc.update("INSERT INTO ai_conversation(conversation_id,user_id,profile_code) VALUES ('alice-chat',101,'GENERAL'),('bob-chat',102,'GENERAL')");
        var traces = new DbAgentTraceRepository(jdbc, new ObjectMapper().findAndRegisterModules());
        traces.saveRun("alice-trace","alice-chat",Instant.now(),"OK");
        traces.saveRun("bob-trace","bob-chat",Instant.now(),"OK");
        assertEquals(1,traces.findForUser(101,null,null,10).size());
        assertTrue(traces.findForUser(101,null,"bob-trace",1).isEmpty());

        var queue = new AttachmentCleanupService(jdbc,mock(io.minio.MinioClient.class),
                new AttachmentProperties(true,"http://localhost:9000","test","test","attachments",1024));
        queue.enqueue("test-object",Instant.now().plusSeconds(3600));
        queue.enqueue("test-object",Instant.now().minusSeconds(1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_attachment_cleanup",Integer.class));
        queue.cleanDue();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_attachment_cleanup",Integer.class));

        var runs = new EvalRunRepository(jdbc);
        runs.save(new EvalRun("run","test","GENERAL",List.of("case"),"admin"));
        assertEquals(1,runs.pendingRuns().size());
        assertTrue(runs.claim("run"));
        assertFalse(runs.claim("run"));
        runs.updateResults("run",EvalRunStatus.COMPLETED,1,0,0,100,100);
        assertEquals(EvalRunStatus.COMPLETED,runs.findByRunId("run").orElseThrow().status());
    }

    @Test void upgradingExistingV12DataPreservesSnapshotsAndAllowsLongVersions() {
        flyway("12").migrate();
        jdbc.update("INSERT INTO sxw_users(id,username,password_hash,nickname) VALUES (101,'alice','test','Alice')");
        jdbc.update("INSERT INTO ai_conversation(conversation_id,user_id,profile_code) VALUES ('chat',101,'GENERAL')");
        jdbc.update("INSERT INTO ai_agent_run(run_id,conversation_id,user_id,request_id,status) VALUES ('run','chat',101,'req','COMPLETED')");
        jdbc.update("INSERT INTO ai_context_snapshot(run_id,call_no,input_tokens,context_hash,context_json,system_prompt_version) VALUES ('run',1,1,'hash','{}'::jsonb,'v1')");
        flyway(null).migrate();
        assertEquals("v1",jdbc.queryForObject("SELECT system_prompt_version FROM ai_context_snapshot",String.class));
        jdbc.update("INSERT INTO ai_context_snapshot(run_id,call_no,input_tokens,context_hash,context_json,system_prompt_version,tool_schema_version) VALUES ('run',2,1,'hash','{}'::jsonb,?,?)","v".repeat(100),"t".repeat(100));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_context_snapshot",Integer.class));
        flyway(null).validate();
    }

    @Test void refreshTokenCompareAndSetHasOneWinnerOnPostgres() throws Exception {
        flyway(null).migrate();
        var tokens = new RefreshTokenRepository(jdbc);
        String hash = "a".repeat(64);
        tokens.save(RefreshToken.create(hash, "alice", Instant.now().plusSeconds(600)));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setTimeout(15);
        var barrier = new CyclicBarrier(4);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> tx.execute(status -> {
                assertTrue(tokens.findByTokenHash(hash).orElseThrow().isActive());
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IllegalStateException(e); }
                return tokens.revokeByTokenHash(hash);
            })));
            int winners = 0;
            for (var result : results) winners += result.get(20, TimeUnit.SECONDS);
            assertEquals(1, winners);
        }
        assertFalse(tokens.findByTokenHash(hash).orElseThrow().isActive());
        String expired = "b".repeat(64);
        tokens.save(RefreshToken.create(expired, "alice", Instant.now().minusSeconds(60)));
        assertEquals(0, tokens.revokeByTokenHash(expired));
    }

    @Test void hermesNowaitLockDoesNotAllowASecondApplicationTransaction() throws Exception {
        flyway(null).migrate();
        jdbc.update("""
                INSERT INTO ai_hermes_candidate(candidate_id,run_id,chat_id,type,title,content,status,reviewed_by,reviewed_at)
                VALUES ('locked','run','chat','MEMORY','test','test','APPROVED','admin',CURRENT_TIMESTAMP)
                """);
        var repository = new HermesCandidateRepository(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setTimeout(15);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var owner = pool.submit(() -> tx.executeWithoutResult(status -> {
                assertTrue(repository.findByIdForUpdate("locked").isPresent());
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock test timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS));
                assertThrows(PessimisticLockingFailureException.class,
                        () -> tx.execute(status -> repository.findByIdForUpdate("locked")));
            } finally {
                release.countDown();
            }
            owner.get(20, TimeUnit.SECONDS);
        }
        assertEquals("APPROVED", repository.findById("locked").orElseThrow().status().name());
        Boolean acquired = tx.execute(status -> repository.findByIdForUpdate("locked").isPresent());
        assertEquals(Boolean.TRUE, acquired);
    }
}
