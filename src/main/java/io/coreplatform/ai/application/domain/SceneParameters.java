package io.coreplatform.ai.application.domain;

public record SceneParameters(
        Double temperature,
        Double topP,
        Integer maxOutputTokens,
        String reasoningEffort,
        boolean jsonMode,
        boolean streaming
) {

    public static SceneParameters defaults() {
        return new SceneParameters(null, null, null, null, false, true);
    }
}
