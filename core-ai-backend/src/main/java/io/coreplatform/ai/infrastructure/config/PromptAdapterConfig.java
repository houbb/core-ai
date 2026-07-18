package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.PromptEvaluationPort;
import io.coreplatform.ai.application.port.PromptPermissionPort;
import io.coreplatform.ai.infrastructure.integration.PreviewPromptEvaluationAdapter;
import io.coreplatform.ai.infrastructure.security.SecurityPromptPermissionAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromptAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(PromptEvaluationPort.class)
    PromptEvaluationPort promptEvaluationPort() {
        return new PreviewPromptEvaluationAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(PromptPermissionPort.class)
    PromptPermissionPort promptPermissionPort() {
        return new SecurityPromptPermissionAdapter();
    }
}
