package io.coreplatform.ai.application.domain;

public record ModelRecommendation(
        ModelData model,
        double score,
        String reason
) {
}
