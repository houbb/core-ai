package io.coreplatform.ai.infrastructure.job;

import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.service.ProviderService;
import io.coreplatform.ai.infrastructure.config.CoreAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProviderHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthScheduler.class);

    private final ProviderService providerService;
    private final CoreAiProperties properties;

    public ProviderHealthScheduler(ProviderService providerService, CoreAiProperties properties) {
        this.providerService = providerService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${core.provider.health-check-delay-ms:300000}")
    public void refreshEnabledProviders() {
        if (!properties.provider().healthCheckEnabled()) {
            return;
        }
        providerService.search(new ProviderSearchCriteria(null, true, null, null, null))
                .forEach(provider -> {
                    try {
                        providerService.testConnection(provider.id());
                    } catch (RuntimeException exception) {
                        log.warn("Scheduled provider health check failed for {}", provider.id(), exception);
                    }
                });
    }
}
