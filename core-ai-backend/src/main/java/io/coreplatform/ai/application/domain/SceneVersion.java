package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record SceneVersion(
        String id,
        String sceneId,
        int version,
        SceneConfiguration configuration,
        Instant createTime,
        String createUser
) {
}
