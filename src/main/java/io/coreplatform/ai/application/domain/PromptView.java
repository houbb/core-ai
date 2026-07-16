package io.coreplatform.ai.application.domain;

public record PromptView(
        PromptData prompt,
        PromptVersionData currentVersion,
        PromptVersionData publishedVersion
) {
}
