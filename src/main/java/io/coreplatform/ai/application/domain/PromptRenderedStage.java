package io.coreplatform.ai.application.domain;

public record PromptRenderedStage(
        String promptId,
        String promptCode,
        int version,
        String systemPrompt,
        String userPrompt,
        String assistantPrompt,
        int estimatedTokens
) {
}
