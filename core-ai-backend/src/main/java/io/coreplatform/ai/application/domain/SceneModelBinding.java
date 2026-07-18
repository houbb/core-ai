package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record SceneModelBinding(
        String id,
        String sceneId,
        String modelAlias,
        int priority,
        boolean fallback,
        boolean enabled,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
