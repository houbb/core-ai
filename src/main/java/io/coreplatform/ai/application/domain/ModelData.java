package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ModelData(
        String id,
        String providerId,
        String providerCode,
        String providerName,
        boolean providerEnabled,
        Long providerLatencyMs,
        String remoteModelId,
        String displayName,
        ModelCategory category,
        String description,
        ModelStatus status,
        boolean enabled,
        boolean availableFromProvider,
        boolean recommended,
        boolean favorite,
        Integer maxContextTokens,
        Integer maxInputTokens,
        Integer maxOutputTokens,
        Integer defaultMaxTokens,
        boolean contextManuallyOverridden,
        Set<Capability> capabilities,
        Map<Capability, Boolean> capabilityOverrides,
        ModelParameters parameters,
        List<ModelPricing> pricingHistory,
        List<ModelAlias> aliases,
        Set<String> tags,
        Instant lastDiscoveredAt,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {

    public ModelData {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        capabilityOverrides = capabilityOverrides == null ? Map.of() : Map.copyOf(capabilityOverrides);
        parameters = parameters == null ? ModelParameters.empty() : parameters;
        pricingHistory = pricingHistory == null ? List.of() : List.copyOf(pricingHistory);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public ModelPricing currentPricing(Instant now) {
        return pricingHistory.stream()
                .filter(price -> !price.effectiveTime().isAfter(now))
                .findFirst()
                .orElse(null);
    }
}
