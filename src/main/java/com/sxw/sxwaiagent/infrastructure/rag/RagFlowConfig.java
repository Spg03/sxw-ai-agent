package com.sxw.sxwaiagent.infrastructure.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagFlowProperties.class)
public class RagFlowConfig {

    @Bean
    public RagFlowClient ragFlowClient(RagFlowProperties properties) {
        return new RagFlowClient(properties);
    }

    @Bean
    public RagFlowKnowledgeService ragFlowKnowledgeService(RagFlowClient ragFlowClient,
                                                           RagFlowProperties properties) {
        return new RagFlowKnowledgeService(ragFlowClient::retrieve, properties);
    }
}
