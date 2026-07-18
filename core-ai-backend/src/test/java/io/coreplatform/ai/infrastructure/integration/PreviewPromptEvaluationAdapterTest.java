package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.PromptTestCase;
import io.coreplatform.ai.application.port.PromptEvaluationPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewPromptEvaluationAdapterTest {

    @Test
    void shouldCompareRenderedPromptWithoutPretendingToExecuteLlm() {
        PromptRenderResult render = new PromptRenderResult(
                "prompt",
                "translate",
                1,
                "System",
                "Hello",
                null,
                List.of(),
                13,
                4,
                null,
                false,
                "PREVIEW"
        );
        PromptTestCase testCase = new PromptTestCase(
                "test",
                "version",
                "render",
                "{}",
                "System\n\nHello",
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        var result = new PreviewPromptEvaluationAdapter().evaluate(
                new PromptEvaluationPort.EvaluationRequest(testCase, render, "trace")
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.mode()).isEqualTo("PREVIEW");
        assertThat(result.executed()).isFalse();
    }
}
