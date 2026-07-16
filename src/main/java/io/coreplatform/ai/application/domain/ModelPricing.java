package io.coreplatform.ai.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelPricing(
        String id,
        String modelId,
        String currency,
        BigDecimal promptPrice,
        BigDecimal completionPrice,
        BigDecimal cacheReadPrice,
        BigDecimal cacheWritePrice,
        Instant effectiveTime,
        String source,
        String notes,
        Instant createTime,
        String createUser
) {
}
