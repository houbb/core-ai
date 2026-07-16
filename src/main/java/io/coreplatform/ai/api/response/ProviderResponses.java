package io.coreplatform.ai.api.response;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ConnectionTestResult;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderHealth;
import io.coreplatform.ai.application.domain.ProviderModel;
import io.coreplatform.ai.application.domain.ProviderStatus;
import io.coreplatform.ai.application.domain.ProviderType;
import io.coreplatform.ai.application.service.ProviderPresetService;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProviderResponses {

    private ProviderResponses() {
    }

    public static ProviderResponse from(
            ProviderData provider,
            List<ProviderModel> models
    ) {
        Map<String, String> maskedHeaders = provider.headers().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> "****"));
        return new ProviderResponse(
                provider.id(),
                provider.code(),
                provider.name(),
                provider.description(),
                provider.type(),
                provider.type().isLocal() ? "LOCAL" : "CLOUD",
                provider.endpoint(),
                provider.enabled(),
                provider.status(),
                provider.priority(),
                provider.weight(),
                provider.timeoutSeconds(),
                provider.retryCount(),
                provider.tags(),
                provider.apiKeyMask(),
                provider.organization(),
                provider.proxy(),
                provider.tlsVerify(),
                maskedHeaders,
                maskSensitiveValues(provider.customParameters()),
                provider.capabilities(),
                HealthResponse.from(provider.health()),
                provider.modelCount(),
                models == null ? null : models.stream().map(ModelResponse::from).toList(),
                provider.createTime(),
                provider.updateTime(),
                provider.createUser(),
                provider.updateUser()
        );
    }

    public static PresetResponse from(ProviderPresetService.ProviderPreset preset) {
        return new PresetResponse(
                preset.code(),
                preset.name(),
                preset.type(),
                preset.endpoint(),
                preset.location(),
                preset.apiKeyRequired(),
                preset.customParameters()
        );
    }

    public static AuditResponse from(AuditEntry entry) {
        return new AuditResponse(
                entry.id(),
                entry.action(),
                entry.result(),
                entry.detail(),
                entry.traceId(),
                entry.createTime(),
                entry.createUser()
        );
    }

    private static Map<String, String> maskSensitiveValues(Map<String, String> values) {
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> isSensitiveKey(entry.getKey()) ? "****" : entry.getValue()
        ));
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("key")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("credential");
    }

    public record ProviderResponse(
            String id,
            String code,
            String name,
            String description,
            ProviderType type,
            String location,
            String endpoint,
            boolean enabled,
            ProviderStatus status,
            int priority,
            int weight,
            int timeoutSeconds,
            int retryCount,
            Set<String> tags,
            String apiKeyMasked,
            String organization,
            String proxy,
            boolean tlsVerify,
            Map<String, String> headers,
            Map<String, String> customParameters,
            Set<Capability> capabilities,
            HealthResponse health,
            int modelCount,
            List<ModelResponse> models,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record HealthResponse(
            Long latencyMs,
            double availability,
            int rpm,
            int tpm,
            Instant lastSuccess,
            Instant lastError,
            String lastErrorMessage,
            Integer lastStatusCode
    ) {

        static HealthResponse from(ProviderHealth health) {
            return new HealthResponse(
                    health.latencyMs(),
                    health.availability(),
                    health.rpm(),
                    health.tpm(),
                    health.lastSuccess(),
                    health.lastError(),
                    health.lastErrorMessage(),
                    health.lastStatusCode()
            );
        }
    }

    public record ModelResponse(
            String id,
            String modelId,
            String displayName,
            Set<Capability> capabilities,
            Integer contextLength,
            String status,
            Instant lastSyncAt
    ) {

        public static ModelResponse from(ProviderModel model) {
            return new ModelResponse(
                    model.id(),
                    model.modelId(),
                    model.displayName(),
                    model.capabilities(),
                    model.contextLength(),
                    model.status(),
                    model.lastSyncAt()
            );
        }
    }

    public record PresetResponse(
            String code,
            String name,
            ProviderType type,
            String endpoint,
            String location,
            boolean apiKeyRequired,
            Map<String, String> customParameters
    ) {
    }

    public record AuditResponse(
            String id,
            String action,
            String result,
            String detail,
            String traceId,
            Instant createTime,
            String createUser
    ) {
    }

    public record ConnectionResponse(
            boolean success,
            ProviderStatus status,
            long latencyMs,
            int modelCount,
            Set<Capability> capabilities,
            List<ConnectionTestResult.ConnectionCheck> checks,
            String errorCode,
            String message,
            String userMessage,
            List<ConnectionModelResponse> models
    ) {

        public static ConnectionResponse from(ConnectionTestResult result) {
            return new ConnectionResponse(
                    result.success(),
                    result.status(),
                    result.latencyMs(),
                    result.modelCount(),
                    result.capabilities(),
                    result.checks(),
                    result.errorCode(),
                    result.message(),
                    result.userMessage(),
                    result.models().stream()
                            .map(model -> new ConnectionModelResponse(
                                    model.modelId(),
                                    model.displayName(),
                                    model.capabilities(),
                                    model.contextLength()
                            ))
                            .toList()
            );
        }
    }

    public record ConnectionModelResponse(
            String modelId,
            String displayName,
            Set<Capability> capabilities,
            Integer contextLength
    ) {
    }
}
