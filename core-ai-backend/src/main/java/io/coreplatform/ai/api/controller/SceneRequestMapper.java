package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.SceneRequests.ModelBindingRequest;
import io.coreplatform.ai.api.request.SceneRequests.ParameterRequest;
import io.coreplatform.ai.api.request.SceneRequests.PermissionRequest;
import io.coreplatform.ai.api.request.SceneRequests.PromptRequest;
import io.coreplatform.ai.api.request.SceneRequests.WorkflowStepRequest;
import io.coreplatform.ai.application.domain.SceneConfiguration;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneWorkflowStep;

import java.util.List;

final class SceneRequestMapper {

    private SceneRequestMapper() {
    }

    static SceneConfiguration configuration(
            String name,
            String description,
            String category,
            String icon,
            boolean recommended,
            List<ModelBindingRequest> models,
            ParameterRequest parameters,
            PromptRequest prompt,
            List<PermissionRequest> permissions,
            List<WorkflowStepRequest> workflow
    ) {
        return new SceneConfiguration(
                name,
                description,
                category,
                icon,
                recommended,
                models == null ? List.of() : models.stream()
                        .map(item -> new SceneModelBinding(
                                null,
                                null,
                                item.modelAlias(),
                                item.priority(),
                                item.fallback(),
                                item.enabled(),
                                null,
                                null,
                                null,
                                null
                        ))
                        .toList(),
                new SceneParameters(
                        parameters.temperature(),
                        parameters.topP(),
                        parameters.maxOutputTokens(),
                        parameters.reasoningEffort(),
                        parameters.jsonMode(),
                        parameters.streaming()
                ),
                prompt == null
                        ? ScenePromptBinding.empty()
                        : new ScenePromptBinding(prompt.promptId(), prompt.promptVersion()),
                permissions == null ? List.of() : permissions.stream()
                        .map(item -> new ScenePermission(
                                null,
                                null,
                                item.type(),
                                item.value(),
                                null,
                                null,
                                null,
                                null
                        ))
                        .toList(),
                workflow == null ? List.of() : workflow.stream()
                        .map(item -> new SceneWorkflowStep(
                                item.code(),
                                item.type(),
                                item.reference(),
                                item.optional()
                        ))
                        .toList()
        );
    }
}
