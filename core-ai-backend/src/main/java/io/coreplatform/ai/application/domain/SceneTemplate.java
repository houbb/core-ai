package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record SceneTemplate(
        String id,
        String defaultCode,
        String templateName,
        String description,
        String category,
        String icon,
        boolean builtin,
        boolean recommended,
        SceneConfiguration configuration,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
