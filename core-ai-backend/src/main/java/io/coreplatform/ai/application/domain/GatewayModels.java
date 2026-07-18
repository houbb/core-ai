package io.coreplatform.ai.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GatewayModels {

    private GatewayModels() {
    }

    public record Gateway(
            String id,
            String code,
            String name,
            String description,
            String status,
            boolean enabled,
            int currentVersion,
            Instant createTime,
            Instant updateTime
    ) {
    }

    public record Route(
            String id,
            String gatewayId,
            String sceneCode,
            String aliasCode,
            String modelId,
            String providerId,
            String routingStrategy,
            int priority,
            int weight,
            boolean localPreferred,
            boolean enabled,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Policy(
            String id,
            String gatewayId,
            Map<String, Object> settings,
            int timeoutSeconds,
            boolean fallbackEnabled,
            boolean streamingEnabled,
            int maxRetry,
            String retryStrategy,
            long retryIntervalMs
    ) {
        public Policy {
            settings = settings == null ? Map.of() : Map.copyOf(settings);
        }

        public boolean cacheEnabled() {
            return Boolean.TRUE.equals(settings.get("cacheEnabled"));
        }

        public int cacheTtlSeconds() {
            Object value = settings.get("cacheTtlSeconds");
            return value instanceof Number number ? Math.max(1, number.intValue()) : 300;
        }
    }

    public record Invocation(
            String requestId,
            String traceId,
            String sceneCode,
            String aliasCode,
            String input,
            Map<String, Object> parameters,
            boolean cacheable,
            boolean streaming,
            String actor
    ) {
        public Invocation {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record ProviderRequest(
            Invocation invocation,
            Route route,
            int timeoutSeconds,
            int attempt
    ) {
    }

    public record ProviderResult(
            boolean executed,
            String mode,
            String output,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            BigDecimal cost,
            String currency,
            String providerId,
            String modelId,
            Map<String, Object> metadata
    ) {
        public ProviderResult {
            cost = cost == null ? BigDecimal.ZERO : cost;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record InvocationResult(
            String requestId,
            String traceId,
            boolean executed,
            String mode,
            String output,
            String sceneCode,
            String aliasCode,
            String providerId,
            String modelId,
            int retryCount,
            int fallbackCount,
            boolean cacheHit,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            BigDecimal cost,
            String currency,
            String status,
            List<Map<String, Object>> trace,
            Instant completedAt
    ) {
        public InvocationResult {
            trace = trace == null ? List.of() : List.copyOf(trace);
        }
    }

    public record Trace(
            String id,
            String gatewayId,
            String requestId,
            String traceId,
            String sceneCode,
            String aliasCode,
            String providerId,
            String modelId,
            int retryCount,
            int fallbackCount,
            boolean cacheHit,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            BigDecimal cost,
            String currency,
            String status,
            String errorCode,
            List<Map<String, Object>> steps,
            Instant createTime
    ) {
    }

    public record Dashboard(
            long requests,
            long successes,
            long errors,
            double successRate,
            double avgLatencyMs,
            BigDecimal totalCost,
            long cacheHits
    ) {
    }
}
