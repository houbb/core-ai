package io.coreplatform.ai.application.domain;

public record PromptDiffLine(
        String section,
        String type,
        int leftLine,
        int rightLine,
        String text
) {
}
