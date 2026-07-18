package io.coreplatform.ai.application.domain;

public record SceneWorkflowStep(
        String code,
        SceneWorkflowStepType type,
        String reference,
        boolean optional
) {
}
