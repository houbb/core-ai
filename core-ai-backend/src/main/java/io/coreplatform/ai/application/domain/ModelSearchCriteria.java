package io.coreplatform.ai.application.domain;

public record ModelSearchCriteria(
        String query,
        String providerId,
        ModelCategory category,
        Capability capability,
        ModelStatus status,
        Boolean enabled,
        Boolean favorite,
        Boolean recommended,
        Boolean available,
        Integer minimumContextTokens,
        String tag
) {
}
