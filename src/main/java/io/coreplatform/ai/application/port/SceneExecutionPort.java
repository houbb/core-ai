package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneExecutionResult;

import java.util.List;
import java.util.Map;

public interface SceneExecutionPort {

    SceneExecutionResult execute(ExecutionRequest request);

    record ExecutionRequest(
            SceneData scene,
            List<ResolvedSceneModel> resolvedModels,
            String input,
            Map<String, Object> variables,
            PromptRenderResult renderedPrompt,
            boolean testMode,
            String traceId
    ) {

        public ExecutionRequest {
            resolvedModels = resolvedModels == null ? List.of() : List.copyOf(resolvedModels);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }
    }
}
