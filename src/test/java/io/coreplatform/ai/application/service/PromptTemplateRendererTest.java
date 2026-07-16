package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.PromptVariable;
import io.coreplatform.ai.application.domain.PromptVariableType;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer(new ObjectMapper());

    @Test
    void shouldRenderTypedVariablesAndDefaults() {
        var result = renderer.render(
                "Translate to {{language}}.",
                "{{content}}",
                "Return {{metadata}}",
                List.of(
                        variable("language", PromptVariableType.STRING, true, "中文"),
                        variable("content", PromptVariableType.STRING, true, null),
                        variable("metadata", PromptVariableType.OBJECT, false, "{\"strict\":true}")
                ),
                Map.of("content", "Hello")
        );

        assertThat(result.systemPrompt()).isEqualTo("Translate to 中文.");
        assertThat(result.userPrompt()).isEqualTo("Hello");
        assertThat(result.assistantPrompt()).isEqualTo("Return {\"strict\":true}");
    }

    @Test
    void shouldRejectMissingAndWrongTypeVariables() {
        PromptVariable count = variable("count", PromptVariableType.INTEGER, true, null);

        assertThatThrownBy(() -> renderer.render(null, "{{count}}", null, List.of(count), Map.of()))
                .isInstanceOf(ProviderOperationException.class)
                .extracting(error -> ((ProviderOperationException) error).errorCode())
                .isEqualTo("AI_PROMPT_VARIABLE_REQUIRED");

        assertThatThrownBy(() -> renderer.render(
                null,
                "{{count}}",
                null,
                List.of(count),
                Map.of("count", "one")
        )).isInstanceOf(ProviderOperationException.class)
                .extracting(error -> ((ProviderOperationException) error).errorCode())
                .isEqualTo("AI_PROMPT_VARIABLE_TYPE_INVALID");
    }

    private PromptVariable variable(
            String name,
            PromptVariableType type,
            boolean required,
            String defaultValue
    ) {
        return new PromptVariable(
                name,
                "version",
                name,
                type,
                required,
                defaultValue,
                null,
                null,
                null,
                null,
                null
        );
    }
}
