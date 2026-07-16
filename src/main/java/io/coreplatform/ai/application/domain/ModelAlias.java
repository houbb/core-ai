package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record ModelAlias(
        String id,
        String alias,
        String modelId,
        String modelDisplayName,
        String providerName,
        String scene,
        int priority,
        boolean enabled,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
