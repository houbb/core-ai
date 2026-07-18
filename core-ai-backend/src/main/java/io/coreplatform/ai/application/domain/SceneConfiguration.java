package io.coreplatform.ai.application.domain;

import java.util.List;

public record SceneConfiguration(
        String name,
        String description,
        String category,
        String icon,
        boolean recommended,
        List<SceneModelBinding> models,
        SceneParameters parameters,
        ScenePromptBinding prompt,
        List<ScenePermission> permissions,
        List<SceneWorkflowStep> workflow
) {

    public SceneConfiguration {
        models = models == null ? List.of() : List.copyOf(models);
        parameters = parameters == null ? SceneParameters.defaults() : parameters;
        prompt = prompt == null ? ScenePromptBinding.empty() : prompt;
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        workflow = workflow == null ? List.of() : List.copyOf(workflow);
    }
}
