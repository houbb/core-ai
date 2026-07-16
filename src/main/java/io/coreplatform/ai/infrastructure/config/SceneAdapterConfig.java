package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.application.port.SceneExecutionPort;
import io.coreplatform.ai.application.port.GatewayInvocationPort;
import io.coreplatform.ai.application.port.ScenePermissionPort;
import io.coreplatform.ai.infrastructure.integration.GatewaySceneExecutionAdapter;
import io.coreplatform.ai.infrastructure.security.SecurityScenePermissionAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SceneAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(SceneExecutionPort.class)
    SceneExecutionPort sceneExecutionPort(GatewayInvocationPort gatewayInvocationPort) {
        return new GatewaySceneExecutionAdapter(gatewayInvocationPort);
    }

    @Bean
    @ConditionalOnMissingBean(ScenePermissionPort.class)
    ScenePermissionPort scenePermissionPort() {
        return new SecurityScenePermissionAdapter();
    }
}
