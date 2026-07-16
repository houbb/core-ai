package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Set;

@Component
public class JsonSchemaValidator {

    private static final Set<String> SUPPORTED = Set.of(
            "$schema",
            "title",
            "description",
            "type",
            "required",
            "properties",
            "items",
            "enum",
            "additionalProperties"
    );

    private final ObjectMapper objectMapper;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        JsonNode schema = parse(schemaJson, "AI_PROMPT_SCHEMA_INVALID", "Output Schema is invalid JSON");
        if (!schema.isObject()) {
            throw invalid("AI_PROMPT_SCHEMA_INVALID", "Output Schema must be a JSON object");
        }
        validateSchemaNode(schema, "$");
    }

    public void validateOutput(String schemaJson, String output) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        JsonNode schema = parse(schemaJson, "AI_PROMPT_SCHEMA_INVALID", "Output Schema is invalid JSON");
        JsonNode value = parse(output, "AI_PROMPT_OUTPUT_JSON_INVALID", "Output is not valid JSON");
        validateValue(schema, value, "$");
    }

    private void validateSchemaNode(JsonNode schema, String path) {
        if (!schema.isObject()) {
            throw invalid("AI_PROMPT_SCHEMA_INVALID", "Schema node must be an object at " + path);
        }
        Iterator<String> names = schema.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!SUPPORTED.contains(name)) {
                throw invalid(
                        "AI_PROMPT_SCHEMA_KEYWORD_UNSUPPORTED",
                        "Unsupported JSON Schema keyword at " + path + ": " + name
                );
            }
        }
        JsonNode type = schema.get("type");
        if (type != null && (!type.isTextual() || !Set.of(
                "object", "array", "string", "integer", "number", "boolean", "null"
        ).contains(type.asText()))) {
            throw invalid("AI_PROMPT_SCHEMA_TYPE_INVALID", "Invalid JSON Schema type at " + path);
        }
        JsonNode required = schema.get("required");
        if (required != null && (!required.isArray()
                || !all(required, JsonNode::isTextual))) {
            throw invalid("AI_PROMPT_SCHEMA_REQUIRED_INVALID", "required must be a string array");
        }
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            if (!properties.isObject()) {
                throw invalid("AI_PROMPT_SCHEMA_PROPERTIES_INVALID", "properties must be an object");
            }
            properties.fields().forEachRemaining(entry ->
                    validateSchemaNode(entry.getValue(), path + "." + entry.getKey())
            );
        }
        JsonNode items = schema.get("items");
        if (items != null) {
            if (!items.isObject()) {
                throw invalid("AI_PROMPT_SCHEMA_ITEMS_INVALID", "items must be an object");
            }
            validateSchemaNode(items, path + "[]");
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && !enumNode.isArray()) {
            throw invalid("AI_PROMPT_SCHEMA_ENUM_INVALID", "enum must be an array");
        }
        JsonNode additional = schema.get("additionalProperties");
        if (additional != null && !additional.isBoolean()) {
            throw invalid(
                    "AI_PROMPT_SCHEMA_ADDITIONAL_INVALID",
                    "additionalProperties must be boolean"
            );
        }
    }

    private void validateValue(JsonNode schema, JsonNode value, String path) {
        JsonNode type = schema.get("type");
        if (type != null && !matches(type.asText(), value)) {
            throw invalid(
                    "AI_PROMPT_OUTPUT_SCHEMA_MISMATCH",
                    "Output type mismatch at " + path + ", expected " + type.asText()
            );
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && !contains(enumNode, value)) {
            throw invalid("AI_PROMPT_OUTPUT_SCHEMA_MISMATCH", "Output value is not allowed at " + path);
        }
        if (value.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null) {
                for (JsonNode item : required) {
                    if (!value.has(item.asText())) {
                        throw invalid(
                                "AI_PROMPT_OUTPUT_SCHEMA_MISMATCH",
                                "Required output property is missing: " + path + "." + item.asText()
                        );
                    }
                }
            }
            JsonNode properties = schema.get("properties");
            if (properties != null) {
                properties.fields().forEachRemaining(entry -> {
                    if (value.has(entry.getKey())) {
                        validateValue(
                                entry.getValue(),
                                value.get(entry.getKey()),
                                path + "." + entry.getKey()
                        );
                    }
                });
            }
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                value.fieldNames().forEachRemaining(name -> {
                    if (properties == null || !properties.has(name)) {
                        throw invalid(
                                "AI_PROMPT_OUTPUT_SCHEMA_MISMATCH",
                                "Unexpected output property: " + path + "." + name
                        );
                    }
                });
            }
        }
        if (value.isArray() && schema.has("items")) {
            int index = 0;
            for (JsonNode item : value) {
                validateValue(schema.get("items"), item, path + "[" + index++ + "]");
            }
        }
    }

    private boolean matches(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode item : array) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean all(JsonNode array, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode item : array) {
            if (!predicate.test(item)) {
                return false;
            }
        }
        return true;
    }

    private JsonNode parse(String json, String code, String message) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalid(code, message);
        }
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }
}
