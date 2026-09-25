package com.sxw.sxwaiagent.hermes;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import java.time.Instant;
import java.util.UUID;
import static com.sxw.sxwaiagent.hermes.HermesCandidate.CandidateStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HermesTransactionTest {
    @Test void failureRollsBackEffectsAndRetryCommitsExactlyOnce() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_hermes_candidate(candidate_id VARCHAR(64) PRIMARY KEY,run_id VARCHAR(64),chat_id VARCHAR(64),type VARCHAR(64),title VARCHAR(255),content TEXT,metadata TEXT,status VARCHAR(32),reviewed_by VARCHAR(64),created_at TIMESTAMP,reviewed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE applied_effect(candidate_id VARCHAR(64) PRIMARY KEY)");
        var repository = new HermesCandidateRepository(jdbc);
        repository.save(new HermesCandidate("c","run","chat",CandidateType.MEMORY,"title","body",null,PENDING,null,Instant.now(),null,"run",null));
        var applier = mock(HermesApplier.class);
        var proxy = new ProxyFactory(new HermesApplicationService(repository,applier));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var service = new HermesCandidateService(repository,(HermesApplicationService)proxy.getProxy());
        when(applier.apply(any())).thenAnswer(call -> {
            jdbc.update("INSERT INTO applied_effect VALUES ('c')");
            throw new IllegalStateException("simulated failure after write");
        });
        assertTrue(service.approveCandidate("c","admin"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM applied_effect",Integer.class));
        assertEquals(APPLY_FAILED,repository.findById("c").orElseThrow().status());
        reset(applier);
        when(applier.apply(any())).thenAnswer(call -> {
            assertTrue(((HermesCandidate)call.getArgument(0)).canApply());
            jdbc.update("INSERT INTO applied_effect VALUES ('c')");
            return "done";
        });
        assertTrue(service.retryApply("c","admin"));
        assertFalse(service.retryApply("c","admin"));
        assertFalse(service.approveCandidate("c","admin"));
        assertFalse(service.rejectCandidate("c","admin"));
        assertEquals(APPLIED,repository.findById("c").orElseThrow().status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM applied_effect",Integer.class));
        verify(applier,times(1)).apply(any());
    }
}
