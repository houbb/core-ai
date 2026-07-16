package io.coreplatform.ai.application.domain;

public record PromptAbAssignment(
        String abTestId,
        String variant,
        int version,
        int bucket
) {
}
