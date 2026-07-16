package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.GatewayModels.Dashboard;
import io.coreplatform.ai.application.domain.GatewayModels.Gateway;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.GatewayModels.Policy;
import io.coreplatform.ai.application.domain.GatewayModels.Route;
import io.coreplatform.ai.application.domain.GatewayModels.Trace;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.GatewayRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGatewayRepository implements GatewayRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcGatewayRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Gateway defaultGateway() {
        return jdbc.query("""
                SELECT id, code, name, description, status, enabled, current_version,
                       create_time, update_time
                  FROM ai_gateway
                 WHERE enabled = TRUE AND status = 'PUBLISHED'
                 ORDER BY code
                 LIMIT 1
                """, Map.of(), this::mapGateway).stream().findFirst()
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_GATEWAY_NOT_AVAILABLE", "No enabled Published Gateway is available", 503
                ));
    }

    @Override
    public List<Gateway> findGateways() {
        return jdbc.query("""
                SELECT id, code, name, description, status, enabled, current_version,
                       create_time, update_time
                  FROM ai_gateway
                 ORDER BY enabled DESC, code
                """, Map.of(), this::mapGateway);
    }

    @Override
    public Policy findPolicy(String gatewayId) {
        return jdbc.query("""
                SELECT p.id, p.gateway_id, p.policy_json, p.timeout_seconds,
                       p.fallback_enabled, p.streaming_enabled,
                       r.max_retry, r.strategy, r.interval_ms
                  FROM ai_gateway_policy p
                  LEFT JOIN ai_gateway_retry r ON r.policy_id = p.id
                 WHERE p.gateway_id = :gatewayId
                """, Map.of("gatewayId", gatewayId), (rs, rowNum) -> new Policy(
                rs.getString("id"),
                rs.getString("gateway_id"),
                objectMap(rs.getString("policy_json")),
                rs.getInt("timeout_seconds"),
                rs.getBoolean("fallback_enabled"),
                rs.getBoolean("streaming_enabled"),
                rs.getInt("max_retry"),
                rs.getString("strategy"),
                rs.getLong("interval_ms")
        )).stream().findFirst().orElseThrow(() -> new ProviderOperationException(
                "AI_GATEWAY_POLICY_NOT_FOUND", "Gateway Policy not found", 404
        ));
    }

    @Override
    public Policy savePolicy(Policy policy, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_gateway_policy
                   SET policy_json = :settings, timeout_seconds = :timeout,
                       fallback_enabled = :fallback, streaming_enabled = :streaming,
                       update_time = :now, update_user = :actor
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", policy.id())
                .addValue("settings", json(policy.settings()))
                .addValue("timeout", policy.timeoutSeconds())
                .addValue("fallback", policy.fallbackEnabled())
                .addValue("streaming", policy.streamingEnabled())
                .addValue("now", now.toString())
                .addValue("actor", actor));
        jdbc.update("""
                UPDATE ai_gateway_retry
                   SET max_retry = :maxRetry, strategy = :strategy, interval_ms = :interval,
                       update_time = :now, update_user = :actor
                 WHERE policy_id = :policyId
                """, new MapSqlParameterSource()
                .addValue("policyId", policy.id())
                .addValue("maxRetry", policy.maxRetry())
                .addValue("strategy", policy.retryStrategy())
                .addValue("interval", policy.retryIntervalMs())
                .addValue("now", now.toString())
                .addValue("actor", actor));
        return findPolicy(policy.gatewayId());
    }

    @Override
    public List<Route> findRoutes(String gatewayId, String sceneCode, String aliasCode) {
        return jdbc.query("""
                SELECT id, gateway_id, scene_code, alias_code, model_id, provider_id,
                       routing_strategy, priority, weight, local_preferred, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_gateway_route
                 WHERE gateway_id = :gatewayId AND enabled = TRUE
                   AND (:sceneCode IS NULL OR scene_code IS NULL OR scene_code = :sceneCode)
                   AND (:aliasCode IS NULL OR alias_code = :aliasCode)
                 ORDER BY local_preferred DESC, priority, weight DESC, id
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("sceneCode", blankToNull(sceneCode))
                .addValue("aliasCode", blankToNull(aliasCode)), this::mapRoute);
    }

    @Override
    public Route saveRoute(Route route) {
        MapSqlParameterSource parameters = routeParameters(route);
        int updated = jdbc.update("""
                UPDATE ai_gateway_route
                   SET scene_code = :sceneCode, alias_code = :aliasCode,
                       model_id = :modelId, provider_id = :providerId,
                       routing_strategy = :strategy, priority = :priority, weight = :weight,
                       local_preferred = :localPreferred, enabled = :enabled,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_gateway_route(
                        id, gateway_id, scene_code, alias_code, model_id, provider_id,
                        routing_strategy, priority, weight, local_preferred, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :gatewayId, :sceneCode, :aliasCode, :modelId, :providerId,
                        :strategy, :priority, :weight, :localPreferred, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
        return route;
    }

    @Override
    public Optional<InvocationResult> findCache(String gatewayId, String cacheKey, Instant now) {
        return jdbc.query("""
                SELECT response_json
                  FROM ai_gateway_cache
                 WHERE gateway_id = :gatewayId
                   AND cache_key = :cacheKey
                   AND expire_time > :now
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("cacheKey", cacheKey)
                .addValue("now", now.toString()), (rs, rowNum) ->
                read(rs.getString("response_json"), InvocationResult.class)
        ).stream().findFirst();
    }

    @Override
    public void saveCache(
            String gatewayId,
            String sceneCode,
            String cacheKey,
            InvocationResult result,
            Instant expiresAt
    ) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE ai_gateway_cache
                   SET response_json = :response, hit_count = hit_count + 1,
                       expire_time = :expiresAt, update_time = :now, update_user = 'gateway'
                 WHERE cache_key = :cacheKey
                """, new MapSqlParameterSource()
                .addValue("response", json(result))
                .addValue("expiresAt", expiresAt.toString())
                .addValue("now", now.toString())
                .addValue("cacheKey", cacheKey));
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_gateway_cache(
                        id, gateway_id, scene_code, cache_key, strategy, response_json,
                        hit_count, expire_time,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :gatewayId, :sceneCode, :cacheKey, 'EXACT', :response,
                        0, :expiresAt,
                        :now, :now, 'gateway', 'gateway'
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("gatewayId", gatewayId)
                    .addValue("sceneCode", sceneCode == null ? "default" : sceneCode)
                    .addValue("cacheKey", cacheKey)
                    .addValue("response", json(result))
                    .addValue("expiresAt", expiresAt.toString())
                    .addValue("now", now.toString()));
        }
    }

    @Override
    public boolean rateLimitAllowed(String gatewayId, String actor, String sceneCode, Instant since) {
        List<Integer> limits = jdbc.query("""
                SELECT rpm
                  FROM ai_gateway_rate_limit
                 WHERE gateway_id = :gatewayId AND enabled = TRUE AND rpm IS NOT NULL
                   AND (
                       (target_type = 'USER' AND target_value = :actor)
                       OR (target_type = 'SCENE' AND target_value = :sceneCode)
                       OR (target_type = 'EVERYONE' AND target_value = '*')
                   )
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("actor", actor)
                .addValue("sceneCode", sceneCode == null ? "" : sceneCode),
                (rs, rowNum) -> rs.getInt("rpm"));
        if (limits.isEmpty()) {
            return true;
        }
        long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM ai_gateway_trace
                 WHERE gateway_id = :gatewayId AND create_time >= :since
                   AND (:sceneCode = '' OR scene_code = :sceneCode)
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("since", since.toString())
                .addValue("sceneCode", sceneCode == null ? "" : sceneCode), Long.class);
        int strictest = limits.stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        return count < strictest;
    }

    @Override
    public void insertTrace(String gatewayId, InvocationResult value) {
        jdbc.update("""
                INSERT INTO ai_gateway_trace(
                    id, gateway_id, request_id, trace_id, scene_code, alias_code,
                    provider_id, model_id, retry_count, fallback_count, cache_hit,
                    input_tokens, output_tokens, latency_ms, cost, currency,
                    status, error_code, trace_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :gatewayId, :requestId, :traceId, :sceneCode, :aliasCode,
                    :providerId, :modelId, :retryCount, :fallbackCount, :cacheHit,
                    :inputTokens, :outputTokens, :latencyMs, :cost, :currency,
                    :status, NULL, :traceJson,
                    :now, :now, 'gateway', 'gateway'
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("gatewayId", gatewayId)
                .addValue("requestId", value.requestId())
                .addValue("traceId", value.traceId())
                .addValue("sceneCode", value.sceneCode())
                .addValue("aliasCode", value.aliasCode())
                .addValue("providerId", value.providerId())
                .addValue("modelId", value.modelId())
                .addValue("retryCount", value.retryCount())
                .addValue("fallbackCount", value.fallbackCount())
                .addValue("cacheHit", value.cacheHit())
                .addValue("inputTokens", value.inputTokens())
                .addValue("outputTokens", value.outputTokens())
                .addValue("latencyMs", value.latencyMs())
                .addValue("cost", value.cost())
                .addValue("currency", value.currency())
                .addValue("status", value.status())
                .addValue("traceJson", json(value.trace()))
                .addValue("now", value.completedAt().toString()));
    }

    @Override
    public List<Trace> findTraces(String gatewayId, int limit) {
        return jdbc.query("""
                SELECT id, gateway_id, request_id, trace_id, scene_code, alias_code,
                       provider_id, model_id, retry_count, fallback_count, cache_hit,
                       input_tokens, output_tokens, latency_ms, cost, currency,
                       status, error_code, trace_json, create_time
                  FROM ai_gateway_trace
                 WHERE gateway_id = :gatewayId
                 ORDER BY create_time DESC
                 LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("limit", Math.max(1, Math.min(limit, 500))), this::mapTrace);
    }

    @Override
    public Dashboard dashboard(String gatewayId, Instant from) {
        return jdbc.query("""
                SELECT COUNT(*) AS requests,
                       SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successes,
                       SUM(CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END) AS errors,
                       COALESCE(AVG(latency_ms), 0) AS avg_latency,
                       COALESCE(SUM(cost), 0) AS total_cost,
                       SUM(CASE WHEN cache_hit = TRUE THEN 1 ELSE 0 END) AS cache_hits
                  FROM ai_gateway_trace
                 WHERE gateway_id = :gatewayId AND create_time >= :from
                """, new MapSqlParameterSource()
                .addValue("gatewayId", gatewayId)
                .addValue("from", from.toString()), (rs, rowNum) -> {
            long requests = rs.getLong("requests");
            long successes = rs.getLong("successes");
            return new Dashboard(
                    requests,
                    successes,
                    rs.getLong("errors"),
                    requests == 0 ? 1 : (double) successes / requests,
                    rs.getDouble("avg_latency"),
                    rs.getBigDecimal("total_cost"),
                    rs.getLong("cache_hits")
            );
        }).stream().findFirst().orElse(new Dashboard(0, 0, 0, 1, 0, BigDecimal.ZERO, 0));
    }

    private Gateway mapGateway(ResultSet rs, int rowNum) throws SQLException {
        return new Gateway(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getBoolean("enabled"),
                rs.getInt("current_version"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time"))
        );
    }

    private Route mapRoute(ResultSet rs, int rowNum) throws SQLException {
        return new Route(
                rs.getString("id"),
                rs.getString("gateway_id"),
                rs.getString("scene_code"),
                rs.getString("alias_code"),
                rs.getString("model_id"),
                rs.getString("provider_id"),
                rs.getString("routing_strategy"),
                rs.getInt("priority"),
                rs.getInt("weight"),
                rs.getBoolean("local_preferred"),
                rs.getBoolean("enabled"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Trace mapTrace(ResultSet rs, int rowNum) throws SQLException {
        return new Trace(
                rs.getString("id"),
                rs.getString("gateway_id"),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getString("scene_code"),
                rs.getString("alias_code"),
                rs.getString("provider_id"),
                rs.getString("model_id"),
                rs.getInt("retry_count"),
                rs.getInt("fallback_count"),
                rs.getBoolean("cache_hit"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getLong("latency_ms"),
                rs.getBigDecimal("cost"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("error_code"),
                listMap(rs.getString("trace_json")),
                Instant.parse(rs.getString("create_time"))
        );
    }

    private MapSqlParameterSource routeParameters(Route value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("gatewayId", value.gatewayId())
                .addValue("sceneCode", value.sceneCode())
                .addValue("aliasCode", value.aliasCode())
                .addValue("modelId", value.modelId())
                .addValue("providerId", value.providerId())
                .addValue("strategy", value.routingStrategy())
                .addValue("priority", value.priority())
                .addValue("weight", value.weight())
                .addValue("localPreferred", value.localPreferred())
                .addValue("enabled", value.enabled())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Gateway data", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Gateway data", exception);
        }
    }

    private Map<String, Object> objectMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Gateway settings", exception);
        }
    }

    private List<Map<String, Object>> listMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Gateway trace", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
