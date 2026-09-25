package com.sxw.sxwaiagent.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvalQueueTest {
    private JdbcTemplate jdbc;
    private EvalRunRepository repository;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("""
            CREATE TABLE ai_eval_run(id BIGSERIAL PRIMARY KEY,run_id VARCHAR(64) UNIQUE,
                run_name VARCHAR(256),status VARCHAR(32),profile_code VARCHAR(32),case_ids TEXT,
                total_cases INT DEFAULT 1,passed_cases INT DEFAULT 0,failed_cases INT DEFAULT 0,
                skipped_cases INT DEFAULT 0,pass_rate NUMERIC(5,2) DEFAULT 0,duration_ms BIGINT DEFAULT 0,
                triggered_by VARCHAR(64),started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP,report_path VARCHAR(512),error_message TEXT)
            """);
        repository = new EvalRunRepository(jdbc);
    }

    private void enqueue(String id) {
        jdbc.update("INSERT INTO ai_eval_run(run_id,run_name,status,profile_code,case_ids,triggered_by) VALUES (?,?,'PENDING','GENERAL','case','admin')",id,id);
    }

    @Test void competingWorkersClaimOnceAndLateErrorCannotOverwriteSuccess() throws Exception {
        enqueue("run");
        assertEquals(1,repository.pendingRuns().size());
        try (var pool=Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<4;i++) jobs.add(pool.submit(()->repository.claim("run")));
            int won=0;
            for(var job:jobs)if(job.get(10,java.util.concurrent.TimeUnit.SECONDS))won++;
            assertEquals(1,won);
        }
        assertTrue(repository.pendingRuns().isEmpty());
        assertEquals(1,repository.findByStatus(EvalRunStatus.RUNNING).size());
        repository.updateResults("run",EvalRunStatus.COMPLETED,1,0,0,100,500);
        repository.updateError("run","late worker error");
        var result=repository.findByRunId("run").orElseThrow();
        assertEquals(EvalRunStatus.COMPLETED,result.status());
        assertEquals(100,result.passRate());
        assertEquals(1,result.passedCases());
        assertNotNull(result.completedAt());
        assertNull(result.errorMessage());
        assertEquals(List.of("case"),result.caseIds());
        assertEquals(1,repository.count());
        assertEquals(result,repository.findRecent(1).getFirst());
        assertEquals(result,repository.findByProfileCode("GENERAL").getFirst());
        assertEquals(result,repository.findAll().getFirst());
    }

    @Test void abandonedRunFailsWithoutRestartingBillableWork() {
        enqueue("old");enqueue("queued");enqueue("active");
        repository.claim("old");repository.claim("active");
        jdbc.update("UPDATE ai_eval_run SET started_at=? WHERE run_id='old'",java.time.LocalDateTime.now().minusHours(3));
        repository.failAbandonedRuns();
        assertEquals(EvalRunStatus.FAILED,repository.findByRunId("old").orElseThrow().status());
        assertEquals(EvalRunStatus.PENDING,repository.findByRunId("queued").orElseThrow().status());
        assertEquals(EvalRunStatus.RUNNING,repository.findByRunId("active").orElseThrow().status());
        assertFalse(repository.claim("old"));
        repository.updateResults("old",EvalRunStatus.COMPLETED,1,0,0,100,500);
        assertEquals(EvalRunStatus.FAILED,repository.findByRunId("old").orElseThrow().status());
        assertTrue(repository.findByRunId("missing").isEmpty());
    }

    @Test void workerSkipsAlreadyClaimedRunAndContinuesOtherQueuedRuns() {
        enqueue("first");enqueue("second");
        var service=mock(EvalService.class);
        doThrow(new IllegalStateException("already claimed")).when(service).executeRun("first");
        new EvalRunWorker(repository,service).poll();
        verify(service).executeRun("first");
        verify(service).executeRun("second");
    }
}
