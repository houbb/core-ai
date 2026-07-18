package io.coreplatform.ai.application.domain;

public record PromptReference(
        String promptId,
        String promptCode,
        int version
) {
}
