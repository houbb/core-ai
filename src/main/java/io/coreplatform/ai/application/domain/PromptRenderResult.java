package io.coreplatform.ai.application.domain;

import java.util.List;

public record PromptRenderResult(
        String promptId,
        String promptCode,
        int version,
        String systemPrompt,
        String userPrompt,
        String assistantPrompt,
        List<PromptRenderedStage> chain,
        int characterCount,
        int estimatedTokens,
        String outputSchema,
        boolean strictSchema,
        String mode
) {

    public PromptRenderResult {
        chain = chain == null ? List.of() : List.copyOf(chain);
    }

    public String combinedPrompt() {
        return String.join("\n\n", List.of(
                systemPrompt == null ? "" : systemPrompt,
                userPrompt == null ? "" : userPrompt,
                assistantPrompt == null ? "" : assistantPrompt
        )).trim();
    }
}
