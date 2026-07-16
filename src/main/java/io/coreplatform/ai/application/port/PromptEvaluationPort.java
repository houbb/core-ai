package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.PromptEvaluationResult;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.PromptTestCase;

public interface PromptEvaluationPort {

    PromptEvaluationResult evaluate(EvaluationRequest request);

    record EvaluationRequest(
            PromptTestCase testCase,
            PromptRenderResult render,
            String traceId
    ) {
    }
}
