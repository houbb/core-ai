package io.coreplatform.ai.application.domain;

public enum PromptGuardrailType {
    SENSITIVE,
    INJECTION,
    ILLEGAL,
    LENGTH,
    JSON_VALIDATE
}
