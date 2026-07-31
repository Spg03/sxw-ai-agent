package com.sxw.sxwaiagent.infrastructure.rag;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagFlowProperties.class)
public class RagFlowConfig {

    @Bean
    public RagFlowClient ragFlowClient(RagFlowProperties properties,
                                       RetryRegistry retryRegistry,
                                       CircuitBreakerRegistry circuitBreakerRegistry,
                                       TimeLimiterRegistry timeLimiterRegistry) {
        return new RagFlowClient(properties,
                retryRegistry.retry("ragflow"),
                circuitBreakerRegistry.circuitBreaker("ragflow"),
                timeLimiterRegistry.timeLimiter("ragflow"));
    }

    @Bean
    public RagFlowKnowledgeService ragFlowKnowledgeService(RagFlowClient ragFlowClient,
                                                           RagFlowProperties properties) {
        return new RagFlowKnowledgeService(ragFlowClient::retrieve, properties);
    }
}
