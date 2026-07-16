package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.AgentPlannerPort;
import io.coreplatform.ai.infrastructure.integration.DeterministicAgentPlannerAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(AgentPlannerPort.class)
    AgentPlannerPort agentPlannerPort() {
        return new DeterministicAgentPlannerAdapter();
    }
}
