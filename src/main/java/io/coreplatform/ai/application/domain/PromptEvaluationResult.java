package io.coreplatform.ai.application.domain;

public record PromptEvaluationResult(
        String testCaseId,
        String testCaseName,
        boolean passed,
        String expectedOutput,
        String actualOutput,
        String mode,
        boolean executed,
        PromptRenderResult render
) {
}
