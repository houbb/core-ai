package io.coreplatform.ai.application.domain;

public record ProviderSearchCriteria(
        String query,
        Boolean enabled,
        String location,
        Capability capability,
        String tag
) {
}
