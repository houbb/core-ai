package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.KnowledgeSourcePort;
import io.coreplatform.ai.infrastructure.integration.PreviewKnowledgeSourceAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(KnowledgeSourcePort.class)
    KnowledgeSourcePort knowledgeSourcePort() {
        return new PreviewKnowledgeSourceAdapter();
    }
}
