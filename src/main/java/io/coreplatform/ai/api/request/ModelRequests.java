package io.coreplatform.ai.api.request;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelRequests {

    private ModelRequests() {
    }

    public record UpdateModelRequest(
            @NotBlank @Size(max = 300) String displayName,
            @NotNull ModelCategory category,
            @Size(max = 4000) String description,
            @Min(1) @Max(10_000_000) Integer maxContextTokens,
            @Min(1) @Max(10_000_000) Integer maxInputTokens,
            @Min(1) @Max(10_000_000) Integer maxOutputTokens,
            @Min(1) @Max(10_000_000) Integer defaultMaxTokens,
            boolean contextManuallyOverridden,
            Set<@Size(max = 100) String> tags
    ) {
    }

    public record CapabilityOverrideRequest(
            @NotNull Map<Capability, @NotNull Boolean> overrides
    ) {
    }

    public record UpdateParametersRequest(
            @DecimalMin("0.0") Double temperature,
            @DecimalMin("0.0") Double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            @Min(1) @Max(10_000_000) Integer maxOutputTokens,
            @Size(max = 40) String reasoningEffort,
            Long seed
    ) {
    }

    public record AddPricingRequest(
            @NotBlank @Size(max = 16) String currency,
            @DecimalMin("0.0") BigDecimal promptPrice,
            @DecimalMin("0.0") BigDecimal completionPrice,
            @DecimalMin("0.0") BigDecimal cacheReadPrice,
            @DecimalMin("0.0") BigDecimal cacheWritePrice,
            @NotNull Instant effectiveTime,
            @Size(max = 2000) String notes
    ) {
    }

    public record StatusRequest(@NotNull ModelStatus status) {
    }

    public record FlagsRequest(boolean favorite, boolean recommended) {
    }

    public record CompareRequest(
            @NotEmpty @Size(min = 2, max = 5) List<String> ids
    ) {
    }

    public record AliasRequest(
            @NotBlank @Size(max = 100) String alias,
            @NotBlank String modelId,
            @Size(max = 100) String scene,
            @Min(0) @Max(10000) int priority,
            boolean enabled
    ) {
    }
}
