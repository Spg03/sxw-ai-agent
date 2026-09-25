package com.sxw.sxwaiagent.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/** Durable DB queue; a dedicated scheduler never blocks other maintenance jobs. */
@Component
@ConditionalOnProperty(name="sxw.eval.queue.enabled", havingValue="true", matchIfMissing=true)
public class EvalRunWorker {
    private static final Logger log = LoggerFactory.getLogger(EvalRunWorker.class);
    private final EvalRunRepository repository;
    private final EvalService service;

    public EvalRunWorker(EvalRunRepository repository, EvalService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString="${sxw.eval.queue.delay-ms:2000}", scheduler="evalRunScheduler")
    public void poll() {
        try {
            repository.failAbandonedRuns();
            for (EvalRun run : repository.pendingRuns()) {
                try { service.executeRun(run.runId()); }
                catch (IllegalStateException ignored) { /* Another worker already claimed it. */ }
            }
        } catch (Exception e) {
            log.error("Eval queue polling failed", e);
        }
    }

    @Configuration
    static class SchedulerConfiguration {
        @Bean(name="taskScheduler")
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name="taskScheduler")
        ThreadPoolTaskScheduler maintenanceScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("maintenance-");
            return scheduler;
        }

        @Bean
        ThreadPoolTaskScheduler evalRunScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("eval-run-");
            return scheduler;
        }
    }
}
