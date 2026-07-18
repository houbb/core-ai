package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AnalyticsModels.AlertRule;
import io.coreplatform.ai.application.domain.AnalyticsModels.Budget;
import io.coreplatform.ai.application.domain.AnalyticsModels.BudgetStatus;
import io.coreplatform.ai.application.domain.AnalyticsModels.Dashboard;
import io.coreplatform.ai.application.domain.AnalyticsModels.Evaluation;
import io.coreplatform.ai.application.domain.AnalyticsModels.Feedback;
import io.coreplatform.ai.application.domain.AnalyticsModels.Ranking;
import io.coreplatform.ai.application.domain.AnalyticsModels.TraceSpan;
import io.coreplatform.ai.application.domain.AnalyticsModels.TriggeredAlert;
import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;
import io.coreplatform.ai.application.port.AnalyticsEventPort;
import io.coreplatform.ai.application.port.AnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository, AnalyticsEventPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAnalyticsRepository.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAnalyticsRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(UsageEvent event) {
        try {
            insertUsage(event);
            insertTrace(event);
            if (event.cost().signum() > 0) {
                insertCost(event);
            }
        } catch (DataAccessException | IllegalStateException exception) {
            log.warn("Analytics event dropped without blocking resource {}:{} trace {}",
                    event.resourceType(), event.resourceId(), event.traceId(), exception);
        }
    }

    @Override
    public Dashboard dashboard(Instant from, Instant to) {
        Map<String, Object> range = Map.of("from", from.toString(), "to", to.toString());
        Summary summary = jdbc.queryForObject("""
                SELECT COUNT(*) AS request_count,
                       SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END) AS error_count,
                       COALESCE(AVG(latency_ms), 0) AS avg_latency,
                       COALESCE(SUM(input_tokens), 0) AS input_tokens,
                       COALESCE(SUM(output_tokens), 0) AS output_tokens,
                       COALESCE(SUM(cost), 0) AS total_cost
                  FROM ai_usage_event
                 WHERE create_time >= :from AND create_time <= :to
                """, range, (rs, rowNum) -> new Summary(
                rs.getLong("request_count"),
                rs.getLong("success_count"),
                rs.getLong("error_count"),
                rs.getDouble("avg_latency"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getBigDecimal("total_cost")
        ));
        double quality = jdbc.queryForObject("""
                SELECT COALESCE(AVG(score), 0)
                  FROM ai_evaluation
                 WHERE create_time >= :from AND create_time <= :to
                """, range, Double.class);
        List<Ranking> rankings = jdbc.query("""
                SELECT resource_type, COALESCE(resource_id, '-') AS resource_id,
                       COUNT(*) AS requests,
                       SUM(input_tokens + output_tokens) AS tokens,
                       COALESCE(SUM(cost), 0) AS cost,
                       COALESCE(AVG(latency_ms), 0) AS avg_latency
                  FROM ai_usage_event
                 WHERE create_time >= :from AND create_time <= :to
                 GROUP BY resource_type, resource_id
                 ORDER BY requests DESC, cost DESC
                 LIMIT 20
                """, range, (rs, rowNum) -> new Ranking(
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getLong("requests"),
                rs.getLong("tokens"),
                rs.getBigDecimal("cost"),
                rs.getDouble("avg_latency")
        ));
        List<BudgetStatus> budgets = budgetStatuses(from, to);
        List<TriggeredAlert> alerts = triggeredAlerts(summary, quality);
        long requestCount = summary == null ? 0 : summary.requestCount();
        long successCount = summary == null ? 0 : summary.successCount();
        return new Dashboard(
                requestCount,
                successCount,
                summary == null ? 0 : summary.errorCount(),
                requestCount == 0 ? 1 : (double) successCount / requestCount,
                summary == null ? 0 : summary.avgLatency(),
                summary == null ? 0 : summary.inputTokens(),
                summary == null ? 0 : summary.outputTokens(),
                summary == null ? BigDecimal.ZERO : summary.totalCost(),
                quality,
                rankings,
                budgets,
                alerts
        );
    }

    @Override
    public Evaluation insertEvaluation(Evaluation value) {
        jdbc.update("""
                INSERT INTO ai_evaluation(
                    id, target_type, target_id, evaluation_type, score, judge,
                    dimensions_json, comment,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :targetType, :targetId, :evaluationType, :score, :judge,
                    :dimensions, :comment,
                    :createTime, :createTime, :createUser, :createUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("targetType", value.targetType())
                .addValue("targetId", value.targetId())
                .addValue("evaluationType", value.evaluationType())
                .addValue("score", value.score())
                .addValue("judge", value.judge())
                .addValue("dimensions", json(value.dimensions()))
                .addValue("comment", value.comment())
                .addValue("createTime", value.createTime().toString())
                .addValue("createUser", value.createUser()));
        return value;
    }

    @Override
    public Feedback insertFeedback(Feedback value) {
        jdbc.update("""
                INSERT INTO ai_feedback(
                    id, conversation_id, message_id, resource_type, resource_id,
                    rating, comment, create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :conversationId, :messageId, :resourceType, :resourceId,
                    :rating, :comment, :createTime, :createTime, :createUser, :createUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("conversationId", value.conversationId())
                .addValue("messageId", value.messageId())
                .addValue("resourceType", value.resourceType())
                .addValue("resourceId", value.resourceId())
                .addValue("rating", value.rating())
                .addValue("comment", value.comment())
                .addValue("createTime", value.createTime().toString())
                .addValue("createUser", value.createUser()));
        return value;
    }

    @Override
    public AlertRule saveAlert(AlertRule value) {
        int updated = jdbc.update("""
                UPDATE ai_alert_rule
                   SET name = :name, metric_name = :metricName,
                       condition_operator = :operator, threshold_value = :threshold,
                       action = :action, scope_json = :scope, enabled = :enabled,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, alertParameters(value));
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_alert_rule(
                        id, name, metric_name, condition_operator, threshold_value,
                        action, scope_json, enabled, last_triggered_time, last_triggered_value,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :name, :metricName, :operator, :threshold,
                        :action, :scope, :enabled, :lastTriggeredTime, :lastTriggeredValue,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, alertParameters(value));
        }
        return value;
    }

    @Override
    public List<AlertRule> findAlerts() {
        return jdbc.query("""
                SELECT id, name, metric_name, condition_operator, threshold_value,
                       action, scope_json, enabled, last_triggered_time, last_triggered_value,
                       create_time, update_time, create_user, update_user
                  FROM ai_alert_rule
                 ORDER BY enabled DESC, name
                """, Map.of(), this::mapAlert);
    }

    @Override
    public Budget saveBudget(Budget value) {
        int updated = jdbc.update("""
                UPDATE ai_budget
                   SET owner_type = :ownerType, owner_id = :ownerId,
                       period_type = :periodType, currency = :currency, amount = :amount,
                       warning_ratio = :warningRatio, limit_action = :limitAction,
                       enabled = :enabled, update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, budgetParameters(value));
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_budget(
                        id, owner_type, owner_id, period_type, currency, amount,
                        warning_ratio, limit_action, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :ownerType, :ownerId, :periodType, :currency, :amount,
                        :warningRatio, :limitAction, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, budgetParameters(value));
        }
        return value;
    }

    @Override
    public List<Budget> findBudgets() {
        return jdbc.query("""
                SELECT id, owner_type, owner_id, period_type, currency, amount,
                       warning_ratio, limit_action, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_budget
                 ORDER BY enabled DESC, owner_type, owner_id
                """, Map.of(), this::mapBudget);
    }

    @Override
    public List<TraceSpan> findTrace(String traceId) {
        return jdbc.query("""
                SELECT id, trace_id, parent_id, span_name, resource_type, resource_id,
                       duration_ms, status, attributes_json, start_time, end_time
                  FROM ai_trace
                 WHERE trace_id = :traceId
                 ORDER BY start_time, id
                """, Map.of("traceId", traceId), (rs, rowNum) -> new TraceSpan(
                rs.getString("id"),
                rs.getString("trace_id"),
                rs.getString("parent_id"),
                rs.getString("span_name"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getLong("duration_ms"),
                rs.getString("status"),
                objectMap(rs.getString("attributes_json")),
                Instant.parse(rs.getString("start_time")),
                parseInstant(rs.getString("end_time"))
        ));
    }

    private void insertUsage(UsageEvent value) {
        jdbc.update("""
                INSERT INTO ai_usage_event(
                    id, request_id, trace_id, event_type, resource_type, resource_id,
                    user_id, department_id, project_id, scene_id, model_id, provider_id,
                    input_tokens, output_tokens, cache_tokens, cost, currency, latency_ms,
                    status, error_code, dimensions_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :requestId, :traceId, :eventType, :resourceType, :resourceId,
                    :userId, :departmentId, :projectId, :sceneId, :modelId, :providerId,
                    :inputTokens, :outputTokens, :cacheTokens, :cost, :currency, :latencyMs,
                    :status, :errorCode, :dimensions,
                    :createTime, :createTime, :createUser, :createUser
                )
                """, eventParameters(value));
    }

    private void insertCost(UsageEvent value) {
        long tokens = value.inputTokens() + value.outputTokens();
        jdbc.update("""
                INSERT INTO ai_cost_record(
                    id, usage_event_id, resource_type, resource_id, token_count,
                    unit_price, amount, currency, cost_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :eventId, :resourceType, :resourceId, :tokenCount,
                    :unitPrice, :amount, :currency, :costTime,
                    :costTime, :costTime, :createUser, :createUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("eventId", value.id())
                .addValue("resourceType", value.resourceType())
                .addValue("resourceId", value.resourceId())
                .addValue("tokenCount", tokens)
                .addValue("unitPrice", tokens == 0
                        ? BigDecimal.ZERO
                        : value.cost().divide(BigDecimal.valueOf(tokens), 8, java.math.RoundingMode.HALF_UP))
                .addValue("amount", value.cost())
                .addValue("currency", value.currency() == null ? "USD" : value.currency())
                .addValue("costTime", value.createTime().toString())
                .addValue("createUser", value.createUser()));
    }

    private void insertTrace(UsageEvent value) {
        Instant start = value.createTime().minusMillis(Math.max(0, value.latencyMs()));
        jdbc.update("""
                INSERT INTO ai_trace(
                    id, trace_id, parent_id, span_name, resource_type, resource_id,
                    duration_ms, status, attributes_json, start_time, end_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :traceId, NULL, :spanName, :resourceType, :resourceId,
                    :durationMs, :status, :attributes, :startTime, :endTime,
                    :endTime, :endTime, :createUser, :createUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("traceId", value.traceId())
                .addValue("spanName", value.eventType())
                .addValue("resourceType", value.resourceType())
                .addValue("resourceId", value.resourceId())
                .addValue("durationMs", value.latencyMs())
                .addValue("status", value.status())
                .addValue("attributes", json(value.dimensions()))
                .addValue("startTime", start.toString())
                .addValue("endTime", value.createTime().toString())
                .addValue("createUser", value.createUser()));
    }

    private List<BudgetStatus> budgetStatuses(Instant from, Instant to) {
        List<BudgetStatus> result = new ArrayList<>();
        for (Budget budget : findBudgets().stream().filter(Budget::enabled).toList()) {
            Instant costFrom = "MONTH".equals(budget.periodType())
                    ? YearMonth.from(to.atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                    : from;
            BigDecimal spent = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(cost), 0)
                      FROM ai_usage_event
                     WHERE create_time >= :from AND create_time <= :to
                       AND (:ownerType <> 'USER' OR user_id = :ownerId)
                       AND (:ownerType <> 'DEPARTMENT' OR department_id = :ownerId)
                       AND (:ownerType <> 'PROJECT' OR project_id = :ownerId)
                    """, new MapSqlParameterSource()
                    .addValue("from", costFrom.toString())
                    .addValue("to", to.toString())
                    .addValue("ownerType", budget.ownerType())
                    .addValue("ownerId", budget.ownerId()), BigDecimal.class);
            BigDecimal safeSpent = spent == null ? BigDecimal.ZERO : spent;
            double ratio = budget.amount().signum() == 0
                    ? (safeSpent.signum() == 0 ? 0 : 1)
                    : safeSpent.divide(budget.amount(), 6, java.math.RoundingMode.HALF_UP).doubleValue();
            String status = ratio >= 1 ? "EXCEEDED" : ratio >= budget.warningRatio() ? "WARNING" : "OK";
            result.add(new BudgetStatus(budget, safeSpent, ratio, status));
        }
        return List.copyOf(result);
    }

    private List<TriggeredAlert> triggeredAlerts(Summary summary, double quality) {
        if (summary == null) {
            return List.of();
        }
        List<TriggeredAlert> result = new ArrayList<>();
        for (AlertRule rule : findAlerts().stream().filter(AlertRule::enabled).toList()) {
            double actual = switch (rule.metricName()) {
                case "request_count" -> summary.requestCount();
                case "error_rate" -> summary.requestCount() == 0
                        ? 0
                        : (double) summary.errorCount() / summary.requestCount();
                case "avg_latency_ms" -> summary.avgLatency();
                case "total_cost" -> summary.totalCost().doubleValue();
                case "quality_score" -> quality;
                default -> Double.NaN;
            };
            if (!Double.isNaN(actual) && matches(actual, rule.operator(), rule.threshold())) {
                result.add(new TriggeredAlert(
                        rule.id(), rule.name(), rule.metricName(), actual, rule.threshold(), rule.action()
                ));
            }
        }
        return List.copyOf(result);
    }

    private boolean matches(double actual, String operator, double threshold) {
        return switch (operator) {
            case "GT" -> actual > threshold;
            case "GTE" -> actual >= threshold;
            case "LT" -> actual < threshold;
            case "LTE" -> actual <= threshold;
            case "EQ" -> Double.compare(actual, threshold) == 0;
            default -> false;
        };
    }

    private MapSqlParameterSource eventParameters(UsageEvent value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("requestId", value.requestId())
                .addValue("traceId", value.traceId())
                .addValue("eventType", value.eventType())
                .addValue("resourceType", value.resourceType())
                .addValue("resourceId", value.resourceId())
                .addValue("userId", value.userId())
                .addValue("departmentId", value.departmentId())
                .addValue("projectId", value.projectId())
                .addValue("sceneId", value.sceneId())
                .addValue("modelId", value.modelId())
                .addValue("providerId", value.providerId())
                .addValue("inputTokens", value.inputTokens())
                .addValue("outputTokens", value.outputTokens())
                .addValue("cacheTokens", value.cacheTokens())
                .addValue("cost", value.cost())
                .addValue("currency", value.currency())
                .addValue("latencyMs", value.latencyMs())
                .addValue("status", value.status())
                .addValue("errorCode", value.errorCode())
                .addValue("dimensions", json(value.dimensions()))
                .addValue("createTime", value.createTime().toString())
                .addValue("createUser", value.createUser());
    }

    private MapSqlParameterSource alertParameters(AlertRule value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("name", value.name())
                .addValue("metricName", value.metricName())
                .addValue("operator", value.operator())
                .addValue("threshold", value.threshold())
                .addValue("action", value.action())
                .addValue("scope", json(value.scope()))
                .addValue("enabled", value.enabled())
                .addValue("lastTriggeredTime", instant(value.lastTriggeredTime()))
                .addValue("lastTriggeredValue", value.lastTriggeredValue())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource budgetParameters(Budget value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("ownerType", value.ownerType())
                .addValue("ownerId", value.ownerId())
                .addValue("periodType", value.periodType())
                .addValue("currency", value.currency())
                .addValue("amount", value.amount())
                .addValue("warningRatio", value.warningRatio())
                .addValue("limitAction", value.limitAction())
                .addValue("enabled", value.enabled())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private AlertRule mapAlert(ResultSet rs, int rowNum) throws SQLException {
        return new AlertRule(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("metric_name"),
                rs.getString("condition_operator"),
                rs.getDouble("threshold_value"),
                rs.getString("action"),
                objectMap(rs.getString("scope_json")),
                rs.getBoolean("enabled"),
                parseInstant(rs.getString("last_triggered_time")),
                nullableDouble(rs, "last_triggered_value"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Budget mapBudget(ResultSet rs, int rowNum) throws SQLException {
        return new Budget(
                rs.getString("id"),
                rs.getString("owner_type"),
                rs.getString("owner_id"),
                rs.getString("period_type"),
                rs.getString("currency"),
                rs.getBigDecimal("amount"),
                rs.getDouble("warning_ratio"),
                rs.getString("limit_action"),
                rs.getBoolean("enabled"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize analytics value", exception);
        }
    }

    private Map<String, Object> objectMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read analytics value", exception);
        }
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private record Summary(
            long requestCount,
            long successCount,
            long errorCount,
            double avgLatency,
            long inputTokens,
            long outputTokens,
            BigDecimal totalCost
    ) {
    }
}
