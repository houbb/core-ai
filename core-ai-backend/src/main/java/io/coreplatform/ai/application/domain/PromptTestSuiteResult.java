package io.coreplatform.ai.application.domain;

import java.util.List;

public record PromptTestSuiteResult(
        String promptId,
        int version,
        boolean passed,
        String mode,
        boolean executed,
        List<PromptEvaluationResult> results
) {

    public PromptTestSuiteResult {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
