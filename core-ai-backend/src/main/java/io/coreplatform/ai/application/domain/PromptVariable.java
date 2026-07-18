package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptVariable(
        String id,
        String promptVersionId,
        String name,
        PromptVariableType type,
        boolean required,
        String defaultValue,
        String description,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
