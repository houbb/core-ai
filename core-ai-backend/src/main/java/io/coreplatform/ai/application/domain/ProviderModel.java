package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.Set;

public record ProviderModel(
        String id,
        String providerId,
        String modelId,
        String displayName,
        Set<Capability> capabilities,
        Integer contextLength,
        String status,
        Instant lastSyncAt,
        Instant createTime,
        Instant updateTime
) {
}
