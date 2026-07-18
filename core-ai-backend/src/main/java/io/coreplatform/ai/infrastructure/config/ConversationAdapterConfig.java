package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.ConversationSummaryPort;
import io.coreplatform.ai.infrastructure.integration.DeterministicConversationSummaryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(ConversationSummaryPort.class)
    ConversationSummaryPort conversationSummaryPort() {
        return new DeterministicConversationSummaryAdapter();
    }
}
