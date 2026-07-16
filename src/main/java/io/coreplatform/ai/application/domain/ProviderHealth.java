package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record ProviderHealth(
        Long latencyMs,
        double availability,
        int rpm,
        int tpm,
        Instant lastSuccess,
        Instant lastError,
        String lastErrorMessage,
        Integer lastStatusCode
) {

    public static ProviderHealth empty() {
        return new ProviderHealth(null, 0, 0, 0, null, null, null, null);
    }
}
