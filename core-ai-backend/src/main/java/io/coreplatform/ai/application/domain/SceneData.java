package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;

public record SceneData(
        String id,
        String code,
        String name,
        String description,
        String category,
        String icon,
        SceneStatus status,
        boolean enabled,
        int version,
        boolean recommended,
        Instant lastTestedAt,
        Integer lastTestedVersion,
        List<SceneModelBinding> models,
        SceneParameters parameters,
        ScenePromptBinding prompt,
        List<ScenePermission> permissions,
        List<SceneWorkflowStep> workflow,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {

    public SceneData {
        models = models == null ? List.of() : List.copyOf(models);
        parameters = parameters == null ? SceneParameters.defaults() : parameters;
        prompt = prompt == null ? ScenePromptBinding.empty() : prompt;
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        workflow = workflow == null ? List.of() : List.copyOf(workflow);
    }

    public SceneConfiguration configuration() {
        return new SceneConfiguration(
                name,
                description,
                category,
                icon,
                recommended,
                models,
                parameters,
                prompt,
                permissions,
                workflow
        );
    }
}
