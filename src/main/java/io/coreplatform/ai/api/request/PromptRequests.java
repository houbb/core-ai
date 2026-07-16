package io.coreplatform.ai.api.request;

import io.coreplatform.ai.application.domain.PromptGuardrailPhase;
import io.coreplatform.ai.application.domain.PromptGuardrailType;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptVariableType;
import io.coreplatform.ai.application.domain.PromptVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class PromptRequests {

    private PromptRequests() {
    }

    public record CreatePromptRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 64) String sceneId,
            PromptVisibility visibility,
            @Size(max = 100) String projectCode,
            @Size(max = 100) String departmentCode,
            @Size(max = 200000) String systemPrompt,
            @NotBlank @Size(max = 200000) String userPrompt,
            @Size(max = 200000) String assistantPrompt,
            @Size(max = 1000) String changeLog,
            @Valid List<VariableRequest> variables,
            @Valid OutputSchemaRequest outputSchema,
            @Valid List<GuardrailRequest> guardrails,
            @Valid List<ChainStepRequest> chain
    ) {
    }

    public record UpdatePromptRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 64) String sceneId,
            PromptVisibility visibility,
            @Size(max = 100) String projectCode,
            @Size(max = 100) String departmentCode,
            @Size(max = 200000) String systemPrompt,
            @NotBlank @Size(max = 200000) String userPrompt,
            @Size(max = 200000) String assistantPrompt,
            @Size(max = 1000) String changeLog,
            @Valid List<VariableRequest> variables,
            @Valid OutputSchemaRequest outputSchema,
            @Valid List<GuardrailRequest> guardrails,
            @Valid List<ChainStepRequest> chain
    ) {
    }

    public record VariableRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull PromptVariableType type,
            boolean required,
            @Size(max = 100000) String defaultValue,
            @Size(max = 1000) String description
    ) {
    }

    public record OutputSchemaRequest(
            @Size(max = 200000) String schemaJson,
            boolean strictMode
    ) {
    }

    public record GuardrailRequest(
            @NotNull PromptGuardrailType type,
            @NotNull PromptGuardrailPhase phase,
            @Size(max = 100000) String configJson,
            boolean enabled
    ) {
    }

    public record ChainStepRequest(
            @NotBlank @Size(max = 100) String reference,
            @Min(1) Integer version,
            boolean optional
    ) {
    }

    public record StatusRequest(@NotNull PromptStatus status) {
    }

    public record RenderRequest(Map<String, Object> variables) {
    }

    public record ValidateOutputRequest(
            @Min(1) Integer version,
            @NotBlank @Size(max = 1000000) String output
    ) {
    }

    public record TestCaseRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500000) String inputJson,
            @Size(max = 500000) String expectedOutput,
            boolean enabled
    ) {
    }

    public record CreateAbTestRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 64) String sceneId,
            @Min(1) int versionA,
            @Min(1) int versionB,
            @Min(1) @Max(99) int trafficRatio
    ) {
    }

    public record AbAssignRequest(
            @NotBlank @Size(max = 500) String subjectKey
    ) {
    }

    public record AbObservationRequest(
            @NotBlank String variant,
            boolean success,
            @Min(0) long latencyMs,
            @DecimalMin("0") BigDecimal cost,
            @DecimalMin("0") @DecimalMax("5") double score
    ) {
    }
}
