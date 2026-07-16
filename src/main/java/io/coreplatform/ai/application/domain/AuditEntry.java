package io.coreplatform.ai.application.domain;

import java.time.Instant;

public record AuditEntry(
        String id,
        String resourceType,
        String resourceId,
        String action,
        String result,
        String detail,
        String traceId,
        Instant createTime,
        String createUser
) {
}
