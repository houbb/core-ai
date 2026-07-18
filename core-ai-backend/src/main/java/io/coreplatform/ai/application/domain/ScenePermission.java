package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record ScenePermission(
        String id,
        String sceneId,
        ScenePermissionType type,
        String value,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
