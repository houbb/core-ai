package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.PromptEvaluationResult;
import io.coreplatform.ai.application.port.PromptEvaluationPort;

public class PreviewPromptEvaluationAdapter implements PromptEvaluationPort {

    @Override
    public PromptEvaluationResult evaluate(EvaluationRequest request) {
        String actual = request.render().combinedPrompt();
        String expected = request.testCase().expectedOutput();
        boolean passed = expected == null
                || expected.isBlank()
                || expected.trim().equals(actual.trim());
        return new PromptEvaluationResult(
                request.testCase().id(),
                request.testCase().name(),
                passed,
                expected,
                actual,
                "PREVIEW",
                false,
                request.render()
        );
    }
}
