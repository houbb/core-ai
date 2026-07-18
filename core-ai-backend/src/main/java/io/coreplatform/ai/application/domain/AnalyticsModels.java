package io.coreplatform.ai.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AnalyticsModels {

    private AnalyticsModels() {
    }

    public record UsageEvent(
            String id,
            String requestId,
            String traceId,
            String eventType,
            String resourceType,
            String resourceId,
            String userId,
            String departmentId,
            String projectId,
            String sceneId,
            String modelId,
            String providerId,
            long inputTokens,
            long outputTokens,
            long cacheTokens,
            BigDecimal cost,
            String currency,
            long latencyMs,
            String status,
            String errorCode,
            Map<String, Object> dimensions,
            Instant createTime,
            String createUser
    ) {
        public UsageEvent {
            cost = cost == null ? BigDecimal.ZERO : cost;
            dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
        }
    }

    public record Dashboard(
            long requestCount,
            long successCount,
            long errorCount,
            double successRate,
            double avgLatencyMs,
            long inputTokens,
            long outputTokens,
            BigDecimal totalCost,
            double averageQuality,
            List<Ranking> rankings,
            List<BudgetStatus> budgets,
            List<TriggeredAlert> alerts
    ) {
    }

    public record Ranking(
            String resourceType,
            String resourceId,
            long requests,
            long tokens,
            BigDecimal cost,
            double avgLatencyMs
    ) {
    }

    public record Evaluation(
            String id,
            String targetType,
            String targetId,
            String evaluationType,
            double score,
            String judge,
            Map<String, Object> dimensions,
            String comment,
            Instant createTime,
            String createUser
    ) {
    }

    public record Feedback(
            String id,
            String conversationId,
            String messageId,
            String resourceType,
            String resourceId,
            int rating,
            String comment,
            Instant createTime,
            String createUser
    ) {
    }

    public record AlertRule(
            String id,
            String name,
            String metricName,
            String operator,
            double threshold,
            String action,
            Map<String, Object> scope,
            boolean enabled,
            Instant lastTriggeredTime,
            Double lastTriggeredValue,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public AlertRule {
            scope = scope == null ? Map.of() : Map.copyOf(scope);
        }
    }

    public record Budget(
            String id,
            String ownerType,
            String ownerId,
            String periodType,
            String currency,
            BigDecimal amount,
            double warningRatio,
            String limitAction,
            boolean enabled,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record BudgetStatus(
            Budget budget,
            BigDecimal spent,
            double ratio,
            String status
    ) {
    }

    public record TriggeredAlert(
            String ruleId,
            String ruleName,
            String metricName,
            double actualValue,
            double threshold,
            String action
    ) {
    }

    public record TraceSpan(
            String id,
            String traceId,
            String parentId,
            String spanName,
            String resourceType,
            String resourceId,
            long durationMs,
            String status,
            Map<String, Object> attributes,
            Instant startTime,
            Instant endTime
    ) {
    }
}
