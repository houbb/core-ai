package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaValidatorTest {

    private final JsonSchemaValidator validator = new JsonSchemaValidator(new ObjectMapper());

    @Test
    void shouldValidateSupportedStructuredOutput() {
        String schema = """
                {
                  "type": "object",
                  "required": ["title", "score"],
                  "properties": {
                    "title": {"type": "string"},
                    "score": {"type": "integer", "enum": [1, 2, 3]}
                  },
                  "additionalProperties": false
                }
                """;

        assertThatCode(() -> validator.validateSchema(schema)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateOutput(
                schema,
                "{\"title\":\"Result\",\"score\":2}"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsupportedKeywordAndMismatchedOutput() {
        assertThatThrownBy(() -> validator.validateSchema(
                "{\"type\":\"string\",\"minLength\":2}"
        )).isInstanceOf(ProviderOperationException.class)
                .extracting(error -> ((ProviderOperationException) error).errorCode())
                .isEqualTo("AI_PROMPT_SCHEMA_KEYWORD_UNSUPPORTED");

        assertThatThrownBy(() -> validator.validateOutput(
                "{\"type\":\"object\",\"required\":[\"title\"]}",
                "{\"other\":true}"
        )).isInstanceOf(ProviderOperationException.class)
                .extracting(error -> ((ProviderOperationException) error).errorCode())
                .isEqualTo("AI_PROMPT_OUTPUT_SCHEMA_MISMATCH");
    }
}
