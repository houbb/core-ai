package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.PromptVariable;
import io.coreplatform.ai.application.domain.PromptVariableType;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptTemplateRenderer {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*}}");

    private final ObjectMapper objectMapper;

    public PromptTemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Set<String> referencedVariables(String... templates) {
        Set<String> names = new HashSet<>();
        for (String template : templates) {
            if (template == null) {
                continue;
            }
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return Set.copyOf(names);
    }

    public RenderedTemplates render(
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            List<PromptVariable> definitions,
            Map<String, Object> input
    ) {
        Map<String, Object> values = normalizeValues(definitions, input);
        return new RenderedTemplates(
                renderText(systemPrompt, values),
                renderText(userPrompt, values),
                renderText(assistantPrompt, values),
                values
        );
    }

    private Map<String, Object> normalizeValues(
            List<PromptVariable> definitions,
            Map<String, Object> input
    ) {
        Map<String, Object> source = input == null ? Map.of() : input;
        Map<String, Object> result = new HashMap<>();
        for (PromptVariable definition : definitions) {
            Object value = source.get(definition.name());
            if (value == null && definition.defaultValue() != null) {
                value = parseDefault(definition);
            }
            if (value == null && definition.required()) {
                throw invalid(
                        "AI_PROMPT_VARIABLE_REQUIRED",
                        "Required Prompt variable is missing: " + definition.name()
                );
            }
            if (value != null) {
                validateType(definition, value);
                result.put(definition.name(), value);
            }
        }
        return Map.copyOf(result);
    }

    private String renderText(String template, Map<String, Object> values) {
        if (template == null || template.isBlank()) {
            return null;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) {
                throw invalid(
                        "AI_PROMPT_VARIABLE_UNRESOLVED",
                        "Prompt variable cannot be resolved: " + name
                );
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(display(values.get(name))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object parseDefault(PromptVariable definition) {
        String value = definition.defaultValue();
        if (definition.type() == PromptVariableType.STRING) {
            return value;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return objectMapper.convertValue(node, Object.class);
        } catch (JsonProcessingException exception) {
            throw invalid(
                    "AI_PROMPT_VARIABLE_DEFAULT_INVALID",
                    "Default value is invalid for variable " + definition.name()
            );
        }
    }

    private void validateType(PromptVariable definition, Object value) {
        boolean valid = switch (definition.type()) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long;
            case BOOLEAN -> value instanceof Boolean;
            case LIST -> value instanceof List<?>;
            case OBJECT -> value instanceof Map<?, ?>;
            case JSON -> isJsonCompatible(value);
        };
        if (!valid) {
            throw invalid(
                    "AI_PROMPT_VARIABLE_TYPE_INVALID",
                    "Variable " + definition.name() + " must be " + definition.type()
            );
        }
    }

    private boolean isJsonCompatible(Object value) {
        try {
            objectMapper.valueToTree(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String display(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw invalid("AI_PROMPT_VARIABLE_JSON_INVALID", "Variable cannot be serialized");
            }
        }
        return String.valueOf(value);
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    public record RenderedTemplates(
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            Map<String, Object> values
    ) {
    }
}
