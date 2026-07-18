package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.GatewayProviderPort;
import io.coreplatform.ai.infrastructure.integration.PreviewGatewayProviderAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(GatewayProviderPort.class)
    GatewayProviderPort gatewayProviderPort() {
        return new PreviewGatewayProviderAdapter();
    }
}
