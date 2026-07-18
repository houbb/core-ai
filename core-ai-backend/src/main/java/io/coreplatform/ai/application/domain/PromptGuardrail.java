package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptGuardrail(
        String id,
        String promptVersionId,
        PromptGuardrailType type,
        PromptGuardrailPhase phase,
        String configJson,
        boolean enabled,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
