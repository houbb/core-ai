package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.DiscoveredModel;

import java.time.Instant;
import java.util.List;

public interface ModelDiscoveryPort {

    void synchronizeDiscovered(
            String providerId,
            List<DiscoveredModel> models,
            Instant now,
            String actor
    );
}
