package io.coreplatform.ai.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PromptAbTest(
        String id,
        String promptId,
        String sceneId,
        String name,
        int versionA,
        int versionB,
        int trafficRatio,
        boolean enabled,
        long sampleA,
        long sampleB,
        long successA,
        long successB,
        long latencyATotal,
        long latencyBTotal,
        BigDecimal costATotal,
        BigDecimal costBTotal,
        double scoreATotal,
        double scoreBTotal,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {
}
