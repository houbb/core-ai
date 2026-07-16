package io.coreplatform.ai.application.domain;

public enum ProviderType {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    OLLAMA,
    LM_STUDIO,
    AZURE_OPENAI,
    CUSTOM;

    public boolean isLocal() {
        return this == OLLAMA || this == LM_STUDIO;
    }

    public boolean usuallyRequiresApiKey() {
        return !isLocal() && this != CUSTOM;
    }
}
