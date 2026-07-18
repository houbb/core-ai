package io.coreplatform.ai.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SceneExecutionResult(
        String mode,
        boolean executed,
        String output,
        String sceneCode,
        int sceneVersion,
        String modelAlias,
        String modelId,
        String modelDisplayName,
        String providerName,
        String promptId,
        Integer promptVersion,
        long latencyMs,
        Long estimatedInputTokens,
        BigDecimal estimatedCost,
        String currency,
        List<TraceStep> trace,
        Instant executeTime,
        String traceId
) {

    public SceneExecutionResult {
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    public record TraceStep(
            int order,
            String stage,
            String name,
            String detail,
            String status
    ) {
    }
}
