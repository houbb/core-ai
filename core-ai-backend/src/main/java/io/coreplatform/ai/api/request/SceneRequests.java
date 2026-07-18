package io.coreplatform.ai.api.request;

import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.domain.SceneWorkflowStepType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class SceneRequests {

    private SceneRequests() {
    }

    public record CreateSceneRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 40) String icon,
            boolean recommended,
            @NotEmpty @Size(max = 20) List<@Valid ModelBindingRequest> models,
            @Valid @NotNull ParameterRequest parameters,
            @Valid PromptRequest prompt,
            @Size(max = 50) List<@Valid PermissionRequest> permissions,
            @Size(max = 50) List<@Valid WorkflowStepRequest> workflow
    ) {
    }

    public record UpdateSceneRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 40) String icon,
            boolean recommended,
            @NotEmpty @Size(max = 20) List<@Valid ModelBindingRequest> models,
            @Valid @NotNull ParameterRequest parameters,
            @Valid PromptRequest prompt,
            @Size(max = 50) List<@Valid PermissionRequest> permissions,
            @Size(max = 50) List<@Valid WorkflowStepRequest> workflow
    ) {
    }

    public record ModelBindingRequest(
            @NotBlank @Size(max = 100) String modelAlias,
            @Min(0) @Max(10000) int priority,
            boolean fallback,
            boolean enabled
    ) {
    }

    public record ParameterRequest(
            @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
            @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
            @Min(1) @Max(10_000_000) Integer maxOutputTokens,
            @Size(max = 40) String reasoningEffort,
            boolean jsonMode,
            boolean streaming
    ) {
    }

    public record PromptRequest(
            @Size(max = 100) String promptId,
            @Min(1) Integer promptVersion
    ) {
    }

    public record PermissionRequest(
            @NotNull ScenePermissionType type,
            @Size(max = 200) String value
    ) {
    }

    public record WorkflowStepRequest(
            @NotBlank @Size(max = 100) String code,
            @NotNull SceneWorkflowStepType type,
            @NotBlank @Size(max = 200) String reference,
            boolean optional
    ) {
    }

    public record StatusRequest(@NotNull SceneStatus status) {
    }

    public record ExecuteRequest(
            @NotBlank @Size(max = 100_000) String input,
            Map<String, Object> variables
    ) {
    }

    public record InstantiateTemplateRequest(
            @Size(max = 100) String code,
            @Size(max = 200) String name
    ) {
    }

    public record SaveTemplateRequest(
            @NotBlank @Size(max = 200) String templateName,
            @NotBlank @Size(max = 100) String defaultCode
    ) {
    }

    public record ImportSceneRequest(
            @Min(1) int formatVersion,
            @NotBlank @Size(max = 100) String code,
            @Min(1) int version,
            @Valid @NotNull UpdateSceneRequest configuration
    ) {
    }
}
