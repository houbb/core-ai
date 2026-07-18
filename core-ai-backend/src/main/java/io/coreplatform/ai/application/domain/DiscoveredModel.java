package io.coreplatform.ai.application.domain;

import java.util.Set;

public record DiscoveredModel(
        String modelId,
        String displayName,
        Set<Capability> capabilities,
        Integer contextLength
) {

    public DiscoveredModel {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
