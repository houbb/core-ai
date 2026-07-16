package io.coreplatform.ai.application.domain;

import java.util.List;

public record SceneView(
        SceneData scene,
        List<ResolvedSceneModel> resolvedModels,
        String costTier
) {

    public SceneView {
        resolvedModels = resolvedModels == null ? List.of() : List.copyOf(resolvedModels);
    }
}
