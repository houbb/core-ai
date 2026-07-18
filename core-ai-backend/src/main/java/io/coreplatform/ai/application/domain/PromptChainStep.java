package io.coreplatform.ai.application.domain;

public record PromptChainStep(
        String reference,
        Integer version,
        boolean optional
) {
}
