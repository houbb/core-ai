package io.coreplatform.ai.application.domain;

public record PromptSearchCriteria(
        String query,
        String category,
        PromptStatus status,
        PromptVisibility visibility,
        String sceneId
) {
}
