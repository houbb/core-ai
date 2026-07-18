package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ConnectionTestResult;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderHealth;
import io.coreplatform.ai.application.domain.ProviderModel;
import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.domain.ProviderStatus;
import io.coreplatform.ai.application.domain.ProviderType;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.exception.ProviderProbeException;
import io.coreplatform.ai.application.port.ProviderProbePort;
import io.coreplatform.ai.application.port.ProviderRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.application.port.SecretCipherPort;
import io.coreplatform.ai.application.port.ModelDiscoveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProviderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");

    private final ProviderRepository repository;
    private final ProviderProbePort probePort;
    private final SecretCipherPort secretCipher;
    private final RequestContextPort requestContext;
    private final CapabilityDetector capabilityDetector;
    private final ModelDiscoveryPort modelDiscovery;

    public ProviderService(
            ProviderRepository repository,
            ProviderProbePort probePort,
            SecretCipherPort secretCipher,
            RequestContextPort requestContext,
            CapabilityDetector capabilityDetector,
            ModelDiscoveryPort modelDiscovery
    ) {
        this.repository = repository;
        this.probePort = probePort;
        this.secretCipher = secretCipher;
        this.requestContext = requestContext;
        this.capabilityDetector = capabilityDetector;
        this.modelDiscovery = modelDiscovery;
    }

    @Transactional
    public ProviderData create(CreateProviderCommand command) {
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String code = normalizeCode(command.code());
        validateCommon(
                code,
                command.name(),
                command.type(),
                command.endpoint(),
                command.priority(),
                command.weight(),
                command.timeoutSeconds(),
                command.retryCount()
        );
        if (repository.existsByCode(code, null)) {
            throw conflict("AI_PROVIDER_CODE_EXISTS", "Provider code already exists");
        }
        validateApiKeyRequirement(command.type(), command.apiKey());

        String id = UUID.randomUUID().toString();
        ProviderData provider = new ProviderData(
                id,
                code,
                command.name().trim(),
                trimToNull(command.description()),
                command.type(),
                normalizeEndpoint(command.endpoint()),
                false,
                ProviderStatus.DRAFT,
                defaultValue(command.priority(), 100),
                defaultValue(command.weight(), 100),
                defaultValue(command.timeoutSeconds(), 15),
                defaultValue(command.retryCount(), 0),
                normalizeTags(command.tags()),
                encryptNullable(command.apiKey()),
                secretCipher.mask(command.apiKey()),
                trimToNull(command.organization()),
                trimToNull(command.proxy()),
                command.tlsVerify() == null || command.tlsVerify(),
                normalizeMap(command.headers()),
                normalizeMap(command.customParameters()),
                Set.of(),
                ProviderHealth.empty(),
                0,
                now,
                now,
                actor,
                actor
        );
        ProviderData saved = repository.insert(provider);
        audit(saved.id(), "CREATE", "SUCCESS", "{\"status\":\"DRAFT\"}", actor, now);
        return saved;
    }

    @Transactional
    public ProviderData update(String providerId, UpdateProviderCommand command) {
        ProviderData current = requireProvider(providerId);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String code = normalizeCode(command.code());
        validateCommon(
                code,
                command.name(),
                command.type(),
                command.endpoint(),
                command.priority(),
                command.weight(),
                command.timeoutSeconds(),
                command.retryCount()
        );
        if (repository.existsByCode(code, providerId)) {
            throw conflict("AI_PROVIDER_CODE_EXISTS", "Provider code already exists");
        }

        String encryptedApiKey = current.encryptedApiKey();
        String apiKeyMask = current.apiKeyMask();
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            encryptedApiKey = secretCipher.encrypt(command.apiKey().trim());
            apiKeyMask = secretCipher.mask(command.apiKey().trim());
        }
        if (command.type().usuallyRequiresApiKey() && encryptedApiKey == null) {
            throw unprocessable("AI_PROVIDER_API_KEY_REQUIRED", "API key is required for this provider type");
        }
        Map<String, String> customParameters = command.customParameters() == null
                ? current.customParameters()
                : preserveMaskedValues(
                        current.customParameters(),
                        normalizeMap(command.customParameters())
                );

        ProviderData next = new ProviderData(
                current.id(),
                code,
                command.name().trim(),
                trimToNull(command.description()),
                command.type(),
                normalizeEndpoint(command.endpoint()),
                current.enabled(),
                current.status(),
                defaultValue(command.priority(), 100),
                defaultValue(command.weight(), 100),
                defaultValue(command.timeoutSeconds(), 15),
                defaultValue(command.retryCount(), 0),
                normalizeTags(command.tags()),
                encryptedApiKey,
                apiKeyMask,
                trimToNull(command.organization()),
                trimToNull(command.proxy()),
                command.tlsVerify() == null || command.tlsVerify(),
                command.headers() == null ? current.headers() : normalizeMap(command.headers()),
                customParameters,
                current.capabilities(),
                current.health(),
                current.modelCount(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        ProviderData saved = repository.update(next);
        audit(providerId, "UPDATE", "SUCCESS", "{\"status\":\"" + saved.status() + "\"}", actor, now);
        return saved;
    }

    public List<ProviderData> search(ProviderSearchCriteria criteria) {
        return repository.search(criteria);
    }

    public ProviderData get(String providerId) {
        return requireProvider(providerId);
    }

    public List<ProviderModel> models(String providerId) {
        requireProvider(providerId);
        return repository.findModels(providerId);
    }

    public List<AuditEntry> audit(String providerId) {
        requireProvider(providerId);
        return repository.findAudit(providerId);
    }

    public ConnectionTestResult testConnection(String providerId) {
        return runProbe(providerId, "TEST_CONNECTION");
    }

    public ConnectionTestResult refreshModels(String providerId) {
        return runProbe(providerId, "REFRESH_MODELS");
    }

    @Transactional
    public ProviderData setEnabled(String providerId, boolean enabled) {
        ProviderData current = requireProvider(providerId);
        if (enabled && current.health().lastSuccess() == null) {
            throw unprocessable("AI_PROVIDER_TEST_REQUIRED", "Test the connection successfully before enabling");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        ProviderStatus status = enabled ? ProviderStatus.AVAILABLE : ProviderStatus.DISABLED;
        repository.updateStatus(providerId, status, enabled, now, actor);
        audit(
                providerId,
                enabled ? "ENABLE" : "DISABLE",
                "SUCCESS",
                "{\"enabled\":" + enabled + "}",
                actor,
                now
        );
        return requireProvider(providerId);
    }

    @Transactional
    public void delete(String providerId) {
        requireProvider(providerId);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.softDelete(providerId, now, actor);
        audit(providerId, "DELETE", "SUCCESS", "{\"softDelete\":true}", actor, now);
    }

    private ConnectionTestResult runProbe(String providerId, String action) {
        ProviderData provider = requireProvider(providerId);
        String actor = requestContext.actor();
        Instant startedAt = Instant.now();
        ProviderStatus previousStatus = provider.status();
        repository.updateStatus(providerId, ProviderStatus.TESTING, provider.enabled(), startedAt, actor);

        try {
            String apiKey = decryptNullable(provider.encryptedApiKey());
            validateApiKeyRequirement(provider.type(), apiKey);
            ProviderProbePort.ProbeResult probeResult = probeWithConfiguredRetries(provider, apiKey);
            List<DiscoveredModel> normalizedModels = probeResult.models().stream()
                    .map(model -> new DiscoveredModel(
                            model.modelId(),
                            model.displayName(),
                            capabilityDetector.detect(model.modelId(), model.capabilities()),
                            model.contextLength()
                    ))
                    .toList();
            Set<Capability> capabilities = capabilityDetector.aggregate(normalizedModels);
            Instant now = Instant.now();
            repository.syncModels(providerId, normalizedModels, now, actor);
            repository.updateCapability(providerId, capabilities, now, actor);
            repository.recordHealthSuccess(providerId, probeResult.latencyMs(), now, actor);
            ProviderStatus status = previousStatus == ProviderStatus.DISABLED
                    ? ProviderStatus.DISABLED
                    : ProviderStatus.AVAILABLE;
            repository.updateStatus(providerId, status, provider.enabled(), now, actor);
            try {
                modelDiscovery.synchronizeDiscovered(providerId, normalizedModels, now, actor);
            } catch (RuntimeException exception) {
                LOGGER.error("Model Registry synchronization failed for provider {}", providerId, exception);
                audit(
                        providerId,
                        "MODEL_REGISTRY_SYNC",
                        "FAILED",
                        "{\"errorType\":\"" + safeJson(exception.getClass().getSimpleName()) + "\"}",
                        actor,
                        now
                );
            }
            audit(
                    providerId,
                    action,
                    "SUCCESS",
                    "{\"latencyMs\":" + probeResult.latencyMs() + ",\"modelCount\":" + normalizedModels.size() + "}",
                    actor,
                    now
            );
            return ConnectionTestResult.success(status, probeResult.latencyMs(), normalizedModels, capabilities);
        } catch (ProviderProbeException exception) {
            long latency = Math.max(0, java.time.Duration.between(startedAt, Instant.now()).toMillis());
            Instant now = Instant.now();
            ProviderStatus restored = previousStatus == ProviderStatus.TESTING ? ProviderStatus.DRAFT : previousStatus;
            repository.recordHealthFailure(
                    providerId,
                    latency,
                    exception.statusCode(),
                    truncate(exception.userMessage(), 1000),
                    now,
                    actor
            );
            repository.updateStatus(providerId, restored, provider.enabled(), now, actor);
            audit(
                    providerId,
                    action,
                    "FAILED",
                    "{\"errorCode\":\"" + safeJson(exception.errorCode()) + "\"}",
                    actor,
                    now
            );
            return ConnectionTestResult.failure(
                    restored,
                    latency,
                    exception.errorCode(),
                    exception.getMessage(),
                    exception.userMessage()
            );
        }
    }

    private ProviderProbePort.ProbeResult probeWithConfiguredRetries(ProviderData provider, String apiKey) {
        ProviderProbeException last = null;
        int attempts = Math.max(1, provider.retryCount() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return probePort.probe(new ProviderProbePort.ProviderConnection(
                        provider.type(),
                        provider.endpoint(),
                        apiKey,
                        provider.organization(),
                        provider.proxy(),
                        provider.tlsVerify(),
                        provider.timeoutSeconds(),
                        provider.headers(),
                        provider.customParameters()
                ));
            } catch (ProviderProbeException exception) {
                last = exception;
                if (!isRetryable(exception) || attempt == attempts) {
                    throw exception;
                }
            }
        }
        throw last;
    }

    private boolean isRetryable(ProviderProbeException exception) {
        return "PROVIDER_TIMEOUT".equals(exception.errorCode())
                || "PROVIDER_NETWORK_ERROR".equals(exception.errorCode())
                || (exception.statusCode() != null && exception.statusCode() >= 500);
    }

    private ProviderData requireProvider(String providerId) {
        return repository.findById(providerId)
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_PROVIDER_NOT_FOUND",
                        "Provider not found",
                        404
                ));
    }

    private void validateCommon(
            String code,
            String name,
            ProviderType type,
            String endpoint,
            Integer priority,
            Integer weight,
            Integer timeoutSeconds,
            Integer retryCount
    ) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw unprocessable(
                    "AI_PROVIDER_CODE_INVALID",
                    "Provider code must contain 2-100 lowercase letters, numbers, dots, underscores or hyphens"
            );
        }
        if (name == null || name.isBlank() || name.trim().length() > 200) {
            throw unprocessable("AI_PROVIDER_NAME_INVALID", "Provider name is required and must not exceed 200 chars");
        }
        if (type == null) {
            throw unprocessable("AI_PROVIDER_TYPE_REQUIRED", "Provider type is required");
        }
        normalizeEndpoint(endpoint);
        int normalizedPriority = defaultValue(priority, 100);
        int normalizedWeight = defaultValue(weight, 100);
        int normalizedTimeout = defaultValue(timeoutSeconds, 15);
        int normalizedRetry = defaultValue(retryCount, 0);
        if (normalizedPriority < 0 || normalizedPriority > 10000) {
            throw unprocessable("AI_PROVIDER_PRIORITY_INVALID", "Priority must be between 0 and 10000");
        }
        if (normalizedWeight < 1 || normalizedWeight > 1000) {
            throw unprocessable("AI_PROVIDER_WEIGHT_INVALID", "Weight must be between 1 and 1000");
        }
        if (normalizedTimeout < 1 || normalizedTimeout > 120) {
            throw unprocessable("AI_PROVIDER_TIMEOUT_INVALID", "Timeout must be between 1 and 120 seconds");
        }
        if (normalizedRetry < 0 || normalizedRetry > 5) {
            throw unprocessable("AI_PROVIDER_RETRY_INVALID", "Retry count must be between 0 and 5");
        }
    }

    private void validateApiKeyRequirement(ProviderType type, String apiKey) {
        if (type.usuallyRequiresApiKey() && (apiKey == null || apiKey.isBlank())) {
            throw new ProviderProbeException(
                    "AI_PROVIDER_API_KEY_REQUIRED",
                    "API key is required",
                    "请填写 API Key。",
                    null
            );
        }
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw unprocessable("AI_PROVIDER_ENDPOINT_REQUIRED", "Endpoint is required");
        }
        try {
            URI uri = URI.create(endpoint.trim());
            if (uri.getScheme() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Only HTTP(S) endpoints without user info are allowed");
            }
            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw unprocessable("AI_PROVIDER_ENDPOINT_INVALID", "Endpoint must be a valid HTTP(S) URL");
        }
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                String normalized = tag.trim().toLowerCase(Locale.ROOT);
                if (normalized.length() > 100) {
                    throw unprocessable("AI_PROVIDER_TAG_INVALID", "Tag must not exceed 100 chars");
                }
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
    }

    private Map<String, String> normalizeMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                result.put(key.trim(), value.trim());
            }
        });
        return Map.copyOf(result);
    }

    private Map<String, String> preserveMaskedValues(
            Map<String, String> current,
            Map<String, String> submitted
    ) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(submitted);
        result.replaceAll((key, value) ->
                "****".equals(value) && current.containsKey(key) ? current.get(key) : value
        );
        return Map.copyOf(result);
    }

    private void audit(
            String providerId,
            String action,
            String result,
            String detail,
            String actor,
            Instant now
    ) {
        repository.addAudit(new AuditEntry(
                UUID.randomUUID().toString(),
                "AI_PROVIDER",
                providerId,
                action,
                result,
                detail,
                requestContext.traceId(),
                now,
                actor
        ));
    }

    private String encryptNullable(String value) {
        return value == null || value.isBlank() ? null : secretCipher.encrypt(value.trim());
    }

    private String decryptNullable(String value) {
        return value == null || value.isBlank() ? null : secretCipher.decrypt(value);
    }

    private int defaultValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private String safeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException unprocessable(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    public record CreateProviderCommand(
            String code,
            String name,
            String description,
            ProviderType type,
            String endpoint,
            Integer priority,
            Integer weight,
            Integer timeoutSeconds,
            Integer retryCount,
            String apiKey,
            String organization,
            String proxy,
            Boolean tlsVerify,
            Map<String, String> headers,
            Map<String, String> customParameters,
            Set<String> tags
    ) {
    }

    public record UpdateProviderCommand(
            String code,
            String name,
            String description,
            ProviderType type,
            String endpoint,
            Integer priority,
            Integer weight,
            Integer timeoutSeconds,
            Integer retryCount,
            String apiKey,
            String organization,
            String proxy,
            Boolean tlsVerify,
            Map<String, String> headers,
            Map<String, String> customParameters,
            Set<String> tags
    ) {
    }
}
