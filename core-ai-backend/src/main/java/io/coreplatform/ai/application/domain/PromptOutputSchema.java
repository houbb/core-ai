package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record PromptOutputSchema(
        String id,
        String promptVersionId,
        String schemaJson,
        boolean strictMode,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {

    public static PromptOutputSchema empty() {
        return new PromptOutputSchema(
                null, null, null, false, null, null, null, null
        );
    }

    public boolean configured() {
        return schemaJson != null && !schemaJson.isBlank();
    }
}
