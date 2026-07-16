package io.coreplatform.ai.api.request;

import io.coreplatform.ai.application.domain.ProviderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

public final class ProviderRequests {

    private ProviderRequests() {
    }

    public record CreateProviderRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull ProviderType type,
            @NotBlank @Size(max = 1000) String endpoint,
            @Min(0) @Max(10000) Integer priority,
            @Min(1) @Max(1000) Integer weight,
            @Min(1) @Max(120) Integer timeoutSeconds,
            @Min(0) @Max(5) Integer retryCount,
            @Size(max = 10000) String apiKey,
            @Size(max = 200) String organization,
            @Size(max = 1000) String proxy,
            Boolean tlsVerify,
            Map<String, String> headers,
            Map<String, String> customParameters,
            Set<@Size(max = 100) String> tags
    ) {
    }

    public record UpdateProviderRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull ProviderType type,
            @NotBlank @Size(max = 1000) String endpoint,
            @Min(0) @Max(10000) Integer priority,
            @Min(1) @Max(1000) Integer weight,
            @Min(1) @Max(120) Integer timeoutSeconds,
            @Min(0) @Max(5) Integer retryCount,
            @Size(max = 10000) String apiKey,
            @Size(max = 200) String organization,
            @Size(max = 1000) String proxy,
            Boolean tlsVerify,
            Map<String, String> headers,
            Map<String, String> customParameters,
            Set<@Size(max = 100) String> tags
    ) {
    }

    public record SetEnabledRequest(boolean enabled) {
    }
}
