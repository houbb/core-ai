package io.coreplatform.ai.application.domain;

public record ScenePromptBinding(
        String promptId,
        Integer promptVersion
) {

    public static ScenePromptBinding empty() {
        return new ScenePromptBinding(null, null);
    }
}
