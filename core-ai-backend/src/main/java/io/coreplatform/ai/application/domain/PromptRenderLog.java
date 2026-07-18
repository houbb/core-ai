package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptRenderLog(
        String id,
        String promptId,
        String promptVersionId,
        String variableNames,
        String variablesJson,
        String renderedPrompt,
        String contentHash,
        int estimatedTokens,
        String mode,
        Instant expireTime,
        Instant createTime,
        String createUser
) {
}
