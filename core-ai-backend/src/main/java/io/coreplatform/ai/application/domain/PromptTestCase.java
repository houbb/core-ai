package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptTestCase(
        String id,
        String promptVersionId,
        String name,
        String inputJson,
        String expectedOutput,
        boolean enabled,
        String lastActualOutput,
        Boolean lastPassed,
        Instant lastRunTime,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
