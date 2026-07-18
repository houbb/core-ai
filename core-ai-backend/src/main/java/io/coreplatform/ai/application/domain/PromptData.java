package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptData(
        String id,
        String code,
        String name,
        String description,
        String category,
        String sceneId,
        PromptStatus status,
        int currentVersion,
        Integer publishedVersion,
        PromptVisibility visibility,
        String projectCode,
        String departmentCode,
        String ownerUser,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
