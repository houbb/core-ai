package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.PromptGuardrail;
import io.coreplatform.ai.application.domain.PromptGuardrailPhase;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PromptGuardrailEngine {

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous",
            "ignore all previous",
            "reveal system prompt",
            "developer message",
            "jailbreak",
            "忽略之前",
            "忽略以上",
            "泄露系统提示",
            "越狱"
    );

    private final ObjectMapper objectMapper;
    private final JsonSchemaValidator schemaValidator;

    public PromptGuardrailEngine(ObjectMapper objectMapper, JsonSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    public void validateConfiguration(PromptGuardrail guardrail) {
        JsonNode config = parseConfig(guardrail.configJson());
        switch (guardrail.type()) {
            case LENGTH -> {
                if (!config.path("maxChars").canConvertToInt()
                        || config.path("maxChars").asInt() < 1) {
                    throw invalid(
                            "AI_PROMPT_GUARDRAIL_CONFIG_INVALID",
                            "LENGTH guardrail requires positive maxChars"
                    );
                }
            }
            case SENSITIVE, ILLEGAL -> validatePatterns(config);
            case INJECTION -> {
                if (config.has("patterns")) {
                    validatePatterns(config);
                }
            }
            case JSON_VALIDATE -> {
                if (guardrail.phase() != PromptGuardrailPhase.OUTPUT) {
                    throw invalid(
                            "AI_PROMPT_GUARDRAIL_PHASE_INVALID",
                            "JSON_VALIDATE guardrail must run in OUTPUT phase"
                    );
                }
            }
        }
    }

    public void validateInput(List<PromptGuardrail> guardrails, Map<String, Object> variables) {
        String input = writeJson(variables == null ? Map.of() : variables);
        validate(guardrails, PromptGuardrailPhase.INPUT, input, null);
    }

    public void validateOutput(
            List<PromptGuardrail> guardrails,
            String output,
            String schemaJson
    ) {
        validate(guardrails, PromptGuardrailPhase.OUTPUT, output, schemaJson);
    }

    private void validate(
            List<PromptGuardrail> guardrails,
            PromptGuardrailPhase phase,
            String value,
            String schemaJson
    ) {
        for (PromptGuardrail guardrail : guardrails) {
            if (!guardrail.enabled() || guardrail.phase() != phase) {
                continue;
            }
            JsonNode config = parseConfig(guardrail.configJson());
            switch (guardrail.type()) {
                case LENGTH -> {
                    int max = config.path("maxChars").asInt();
                    if (value != null && value.length() > max) {
                        blocked("LENGTH", "Prompt content exceeds " + max + " characters");
                    }
                }
                case SENSITIVE -> rejectPatterns("SENSITIVE", value, patterns(config));
                case ILLEGAL -> rejectPatterns("ILLEGAL", value, patterns(config));
                case INJECTION -> {
                    List<String> patterns = new ArrayList<>(INJECTION_PATTERNS);
                    patterns.addAll(patterns(config));
                    rejectPatterns("INJECTION", value, patterns);
                }
                case JSON_VALIDATE -> schemaValidator.validateOutput(schemaJson, value);
            }
        }
    }

    private void rejectPatterns(String rule, String value, List<String> patterns) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (!pattern.isBlank() && normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                blocked(rule, "Prompt guardrail blocked content");
            }
        }
    }

    private List<String> patterns(JsonNode config) {
        JsonNode values = config.get("patterns");
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private void validatePatterns(JsonNode config) {
        JsonNode patterns = config.get("patterns");
        if (patterns == null || !patterns.isArray() || patterns.isEmpty()) {
            throw invalid(
                    "AI_PROMPT_GUARDRAIL_CONFIG_INVALID",
                    "Guardrail requires a non-empty patterns array"
            );
        }
        patterns.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid(
                        "AI_PROMPT_GUARDRAIL_CONFIG_INVALID",
                        "Guardrail patterns must be non-empty strings"
                );
            }
        });
    }

    private JsonNode parseConfig(String configJson) {
        try {
            JsonNode node = objectMapper.readTree(
                    configJson == null || configJson.isBlank() ? "{}" : configJson
            );
            if (!node.isObject()) {
                throw invalid("AI_PROMPT_GUARDRAIL_CONFIG_INVALID", "Guardrail config must be an object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw invalid("AI_PROMPT_GUARDRAIL_CONFIG_INVALID", "Guardrail config is invalid JSON");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("AI_PROMPT_GUARDRAIL_INPUT_INVALID", "Guardrail input cannot be serialized");
        }
    }

    private void blocked(String rule, String message) {
        throw new ProviderOperationException("AI_PROMPT_GUARDRAIL_" + rule, message, 422);
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }
}
