package io.coreplatform.ai.application.domain;

public record SceneSearchCriteria(
        String query,
        String category,
        SceneStatus status,
        Boolean enabled,
        Boolean recommended
) {
}
