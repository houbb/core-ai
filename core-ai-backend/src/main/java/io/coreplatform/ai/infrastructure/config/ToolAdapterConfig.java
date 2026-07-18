package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.ToolExecutionPort;
import io.coreplatform.ai.infrastructure.integration.SafeToolExecutionAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(ToolExecutionPort.class)
    ToolExecutionPort toolExecutionPort() {
        return new SafeToolExecutionAdapter();
    }
}
