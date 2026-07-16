package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderType;

import java.util.List;
import java.util.Map;

public interface ProviderProbePort {

    ProbeResult probe(ProviderConnection connection);

    record ProviderConnection(
            ProviderType type,
            String endpoint,
            String apiKey,
            String organization,
            String proxy,
            boolean tlsVerify,
            int timeoutSeconds,
            Map<String, String> headers,
            Map<String, String> customParameters
    ) {
    }

    record ProbeResult(long latencyMs, List<DiscoveredModel> models) {
    }
}
