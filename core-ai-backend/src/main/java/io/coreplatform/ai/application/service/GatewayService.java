package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;
import io.coreplatform.ai.application.domain.GatewayModels.Dashboard;
import io.coreplatform.ai.application.domain.GatewayModels.Gateway;
import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.GatewayModels.Policy;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderRequest;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderResult;
import io.coreplatform.ai.application.domain.GatewayModels.Route;
import io.coreplatform.ai.application.domain.GatewayModels.Trace;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.AnalyticsEventPort;
import io.coreplatform.ai.application.port.GatewayInvocationPort;
import io.coreplatform.ai.application.port.GatewayProviderPort;
import io.coreplatform.ai.application.port.GatewayRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GatewayService implements GatewayInvocationPort {

    private final GatewayRepository repository;
    private final GatewayProviderPort provider;
    private final AnalyticsEventPort analytics;
    private final RequestContextPort requestContext;
    private final ObjectMapper objectMapper;

    public GatewayService(
            GatewayRepository repository,
            GatewayProviderPort provider,
            AnalyticsEventPort analytics,
            RequestContextPort requestContext,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.provider = provider;
        this.analytics = analytics;
        this.requestContext = requestContext;
        this.objectMapper = objectMapper;
    }

    public List<Gateway> gateways() {
        return repository.findGateways();
    }

    public List<Route> routes(String gatewayId) {
        return repository.findRoutes(gatewayId, null, null);
    }

    @Transactional
    public Route saveRoute(
            String id,
            String gatewayId,
            String sceneCode,
            String aliasCode,
            String modelId,
            String providerId,
            String strategy,
            int priority,
            int weight,
            boolean localPreferred,
            boolean enabled
    ) {
        required(aliasCode, "Alias code");
        if (priority < 0 || weight < 1 || weight > 1000) {
            throw invalid("AI_GATEWAY_ROUTE_INVALID", "Route priority or weight is invalid");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        return repository.saveRoute(new Route(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                required(gatewayId, "Gateway id"),
                trim(sceneCode),
                aliasCode.trim(),
                trim(modelId),
                trim(providerId),
                strategy == null || strategy.isBlank() ? "PRIORITY" : strategy.toUpperCase(),
                priority,
                weight,
                localPreferred,
                enabled,
                now,
                now,
                actor,
                actor
        ));
    }

    public Policy policy(String gatewayId) {
        return repository.findPolicy(gatewayId);
    }

    @Transactional
    public Policy savePolicy(
            String gatewayId,
            Map<String, Object> settings,
            int timeoutSeconds,
            boolean fallbackEnabled,
            boolean streamingEnabled,
            int maxRetry,
            String retryStrategy,
            long retryIntervalMs
    ) {
        if (timeoutSeconds < 1 || timeoutSeconds > 300 || maxRetry < 0 || maxRetry > 10
                || retryIntervalMs < 0 || retryIntervalMs > 60_000) {
            throw invalid("AI_GATEWAY_POLICY_INVALID", "Gateway timeout or retry policy is invalid");
        }
        Policy current = repository.findPolicy(gatewayId);
        return repository.savePolicy(new Policy(
                current.id(),
                current.gatewayId(),
                settings,
                timeoutSeconds,
                fallbackEnabled,
                streamingEnabled,
                maxRetry,
                retryStrategy == null || retryStrategy.isBlank() ? "EXPONENTIAL" : retryStrategy.toUpperCase(),
                retryIntervalMs
        ), Instant.now(), requestContext.actor());
    }

    public List<Trace> traces(String gatewayId, int limit) {
        return repository.findTraces(gatewayId, limit);
    }

    public Dashboard dashboard(String gatewayId) {
        return repository.dashboard(gatewayId, Instant.now().minus(30, ChronoUnit.DAYS));
    }

    @Override
    @Transactional
    public InvocationResult invoke(Invocation original) {
        Gateway gateway = repository.defaultGateway();
        Policy policy = repository.findPolicy(gateway.id());
        String actor = original.actor() == null || original.actor().isBlank()
                ? requestContext.actor()
                : original.actor();
        Invocation invocation = new Invocation(
                blankOr(original.requestId(), UUID.randomUUID().toString()),
                blankOr(original.traceId(), requestContext.traceId()),
                trim(original.sceneCode()),
                blankOr(original.aliasCode(), "default"),
                original.input() == null ? "" : original.input(),
                original.parameters(),
                original.cacheable(),
                original.streaming() && policy.streamingEnabled(),
                actor
        );
        if (!repository.rateLimitAllowed(
                gateway.id(), actor, invocation.sceneCode(), Instant.now().minus(1, ChronoUnit.MINUTES)
        )) {
            throw new ProviderOperationException(
                    "AI_GATEWAY_RATE_LIMITED", "Gateway rate limit exceeded", 429
            );
        }

        String cacheKey = cacheKey(invocation);
        if (policy.cacheEnabled() && invocation.cacheable()) {
            InvocationResult cached = repository.findCache(gateway.id(), cacheKey, Instant.now())
                    .map(value -> cached(value, invocation))
                    .orElse(null);
            if (cached != null) {
                repository.insertTrace(gateway.id(), cached);
                recordAnalytics(cached, actor);
                return cached;
            }
        }

        List<Route> routes = repository.findRoutes(
                gateway.id(), invocation.sceneCode(), invocation.aliasCode()
        );
        List<Route> candidates = routes.isEmpty()
                ? java.util.Collections.singletonList(null)
                : routes;
        List<Map<String, Object>> trace = new ArrayList<>();
        long started = System.nanoTime();
        RuntimeException lastFailure = null;
        int retries = 0;
        int fallbacks = 0;
        ProviderResult providerResult = null;

        for (int routeIndex = 0; routeIndex < candidates.size(); routeIndex++) {
            Route candidate = candidates.get(routeIndex);
            int attempts = policy.maxRetry() + 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    trace.add(step("ROUTE", candidate == null ? invocation.aliasCode() : candidate.aliasCode(),
                            routeIndex == 0 ? "PRIMARY" : "FALLBACK", "SELECTED"));
                    providerResult = provider.execute(new ProviderRequest(
                            invocation, candidate, policy.timeoutSeconds(), attempt
                    ));
                    lastFailure = null;
                    break;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    trace.add(step("PROVIDER", candidate == null ? "preview" : candidate.providerId(),
                            exception.getClass().getSimpleName(), "FAILED"));
                    if (attempt < attempts) {
                        retries++;
                    }
                }
            }
            if (providerResult != null || !policy.fallbackEnabled()) {
                break;
            }
            if (routeIndex + 1 < candidates.size()) {
                fallbacks++;
            }
        }
        if (providerResult == null) {
            throw new ProviderOperationException(
                    "AI_GATEWAY_EXECUTION_FAILED",
                    lastFailure == null ? "Gateway execution failed" : lastFailure.getMessage(),
                    502
            );
        }

        long latency = Math.max(providerResult.latencyMs(), (System.nanoTime() - started) / 1_000_000L);
        trace.add(step("OUTPUT", providerResult.mode(), providerResult.output(), "SUCCESS"));
        InvocationResult result = new InvocationResult(
                invocation.requestId(),
                invocation.traceId(),
                providerResult.executed(),
                providerResult.mode(),
                providerResult.output(),
                invocation.sceneCode(),
                invocation.aliasCode(),
                providerResult.providerId(),
                providerResult.modelId(),
                retries,
                fallbacks,
                false,
                providerResult.inputTokens(),
                providerResult.outputTokens(),
                latency,
                providerResult.cost(),
                providerResult.currency(),
                "SUCCESS",
                trace,
                Instant.now()
        );
        repository.insertTrace(gateway.id(), result);
        if (policy.cacheEnabled() && invocation.cacheable()) {
            repository.saveCache(
                    gateway.id(),
                    invocation.sceneCode(),
                    cacheKey,
                    result,
                    Instant.now().plusSeconds(policy.cacheTtlSeconds())
            );
        }
        recordAnalytics(result, actor);
        return result;
    }

    private InvocationResult cached(InvocationResult source, Invocation invocation) {
        List<Map<String, Object>> trace = new ArrayList<>(source.trace());
        trace.add(step("CACHE", "EXACT", "Gateway local cache", "HIT"));
        return new InvocationResult(
                invocation.requestId(),
                invocation.traceId(),
                source.executed(),
                source.mode(),
                source.output(),
                invocation.sceneCode(),
                invocation.aliasCode(),
                source.providerId(),
                source.modelId(),
                0,
                0,
                true,
                source.inputTokens(),
                source.outputTokens(),
                0,
                source.cost(),
                source.currency(),
                source.status(),
                trace,
                Instant.now()
        );
    }

    private void recordAnalytics(InvocationResult result, String actor) {
        analytics.record(new UsageEvent(
                UUID.randomUUID().toString(),
                result.requestId(),
                result.traceId(),
                "GATEWAY_INVOKE",
                "GATEWAY",
                result.sceneCode() == null ? result.aliasCode() : result.sceneCode(),
                actor,
                null,
                null,
                result.sceneCode(),
                result.modelId(),
                result.providerId(),
                result.inputTokens(),
                result.outputTokens(),
                result.cacheHit() ? result.inputTokens() : 0,
                result.cost() == null ? BigDecimal.ZERO : result.cost(),
                result.currency(),
                result.latencyMs(),
                result.status(),
                null,
                Map.of(
                        "mode", result.mode(),
                        "cacheHit", result.cacheHit(),
                        "retryCount", result.retryCount(),
                        "fallbackCount", result.fallbackCount()
                ),
                result.completedAt(),
                actor
        ));
    }

    private Map<String, Object> step(String stage, String name, String detail, String status) {
        return Map.of(
                "stage", stage,
                "name", name == null ? "-" : name,
                "detail", detail == null ? "-" : detail,
                "status", status,
                "time", Instant.now().toString()
        );
    }

    private String cacheKey(Invocation invocation) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(Map.of(
                    "scene", invocation.sceneCode() == null ? "" : invocation.sceneCode(),
                    "alias", invocation.aliasCode(),
                    "input", invocation.input(),
                    "parameters", invocation.parameters()
            ));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create Gateway cache key", exception);
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_GATEWAY_FIELD_REQUIRED", label + " is required");
        }
        return value.trim();
    }

    private String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }
}
