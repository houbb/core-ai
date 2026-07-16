package io.coreplatform.ai.application.domain;

public record ResolvedSceneModel(
        SceneModelBinding binding,
        ModelData model
) {
}
