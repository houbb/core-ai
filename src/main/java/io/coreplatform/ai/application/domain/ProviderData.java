package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ProviderData(
        String id,
        String code,
        String name,
        String description,
        ProviderType type,
        String endpoint,
        boolean enabled,
        ProviderStatus status,
        int priority,
        int weight,
        int timeoutSeconds,
        int retryCount,
        Set<String> tags,
        String encryptedApiKey,
        String apiKeyMask,
        String organization,
        String proxy,
        boolean tlsVerify,
        Map<String, String> headers,
        Map<String, String> customParameters,
        Set<Capability> capabilities,
        ProviderHealth health,
        int modelCount,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {

    public ProviderData {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        customParameters = customParameters == null ? Map.of() : Map.copyOf(customParameters);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        health = health == null ? ProviderHealth.empty() : health;
    }

    public ProviderData withStatus(ProviderStatus nextStatus, boolean nextEnabled, Instant now, String actor) {
        return new ProviderData(
                id, code, name, description, type, endpoint, nextEnabled, nextStatus,
                priority, weight, timeoutSeconds, retryCount, tags, encryptedApiKey,
                apiKeyMask, organization, proxy, tlsVerify, headers, customParameters,
                capabilities, health, modelCount, createTime, now, createUser, actor
        );
    }
}
