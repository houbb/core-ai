package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AnalyticsModels.AlertRule;
import io.coreplatform.ai.application.domain.AnalyticsModels.Budget;
import io.coreplatform.ai.application.domain.AnalyticsModels.Dashboard;
import io.coreplatform.ai.application.domain.AnalyticsModels.Evaluation;
import io.coreplatform.ai.application.domain.AnalyticsModels.Feedback;
import io.coreplatform.ai.application.domain.AnalyticsModels.TraceSpan;
import io.coreplatform.ai.application.port.AnalyticsRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final RequestContextPort requestContext;

    public AnalyticsService(AnalyticsRepository repository, RequestContextPort requestContext) {
        this.repository = repository;
        this.requestContext = requestContext;
    }

    public Dashboard dashboard(Instant from, Instant to) {
        Instant safeTo = to == null ? Instant.now() : to;
        Instant safeFrom = from == null ? safeTo.minus(30, ChronoUnit.DAYS) : from;
        if (safeFrom.isAfter(safeTo)) {
            throw invalid("AI_ANALYTICS_RANGE_INVALID", "Analytics start time must not be after end time");
        }
        return repository.dashboard(safeFrom, safeTo);
    }

    public List<TraceSpan> trace(String traceId) {
        return repository.findTrace(required(traceId, "Trace id"));
    }

    @Transactional
    public Evaluation evaluate(
            String targetType,
            String targetId,
            String evaluationType,
            double score,
            String judge,
            Map<String, Object> dimensions,
            String comment
    ) {
        if (score < 0 || score > 5) {
            throw invalid("AI_EVALUATION_SCORE_INVALID", "Evaluation score must be between 0 and 5");
        }
        String actor = requestContext.actor();
        return repository.insertEvaluation(new Evaluation(
                UUID.randomUUID().toString(),
                required(targetType, "Target type").toUpperCase(),
                required(targetId, "Target id"),
                required(evaluationType, "Evaluation type").toUpperCase(),
                score,
                judge == null || judge.isBlank() ? actor : judge.trim(),
                dimensions,
                trim(comment, 4000),
                Instant.now(),
                actor
        ));
    }

    @Transactional
    public Feedback feedback(
            String conversationId,
            String messageId,
            String resourceType,
            String resourceId,
            int rating,
            String comment
    ) {
        if (rating < -1 || rating > 5 || rating == 0) {
            throw invalid("AI_FEEDBACK_RATING_INVALID", "Feedback rating must be -1 or between 1 and 5");
        }
        String actor = requestContext.actor();
        return repository.insertFeedback(new Feedback(
                UUID.randomUUID().toString(),
                trim(conversationId, 64),
                trim(messageId, 64),
                trim(resourceType, 40),
                trim(resourceId, 100),
                rating,
                trim(comment, 4000),
                Instant.now(),
                actor
        ));
    }

    @Transactional
    public AlertRule saveAlert(
            String id,
            String name,
            String metricName,
            String operator,
            double threshold,
            String action,
            Map<String, Object> scope,
            boolean enabled
    ) {
        String actor = requestContext.actor();
        Instant now = Instant.now();
        AlertRule current = id == null ? null : repository.findAlerts().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
        return repository.saveAlert(new AlertRule(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                required(name, "Alert name"),
                required(metricName, "Metric name"),
                normalizeOperator(operator),
                threshold,
                required(action, "Alert action").toUpperCase(),
                scope,
                enabled,
                current == null ? null : current.lastTriggeredTime(),
                current == null ? null : current.lastTriggeredValue(),
                current == null ? now : current.createTime(),
                now,
                current == null ? actor : current.createUser(),
                actor
        ));
    }

    public List<AlertRule> alerts() {
        return repository.findAlerts();
    }

    @Transactional
    public Budget saveBudget(
            String id,
            String ownerType,
            String ownerId,
            String periodType,
            String currency,
            BigDecimal amount,
            double warningRatio,
            String limitAction,
            boolean enabled
    ) {
        if (amount == null || amount.signum() < 0) {
            throw invalid("AI_BUDGET_AMOUNT_INVALID", "Budget amount must not be negative");
        }
        if (warningRatio <= 0 || warningRatio > 1) {
            throw invalid("AI_BUDGET_RATIO_INVALID", "Budget warning ratio must be in (0, 1]");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Budget current = id == null ? null : repository.findBudgets().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
        return repository.saveBudget(new Budget(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                required(ownerType, "Owner type").toUpperCase(),
                required(ownerId, "Owner id"),
                required(periodType, "Period type").toUpperCase(),
                required(currency, "Currency").toUpperCase(),
                amount,
                warningRatio,
                required(limitAction, "Limit action").toUpperCase(),
                enabled,
                current == null ? now : current.createTime(),
                now,
                current == null ? actor : current.createUser(),
                actor
        ));
    }

    public List<Budget> budgets() {
        return repository.findBudgets();
    }

    public String insight(Instant from, Instant to) {
        Dashboard value = dashboard(from, to);
        String trend = value.successRate() >= 0.98 ? "stable" : value.successRate() >= 0.9 ? "watch" : "critical";
        String cost = value.totalCost().signum() == 0
                ? "No billable external inference was recorded."
                : "Recorded cost is " + value.totalCost().stripTrailingZeros().toPlainString() + ".";
        return "Runtime is " + trend + ": " + value.requestCount() + " requests, "
                + String.format("%.1f", value.successRate() * 100) + "% success, "
                + String.format("%.1f", value.avgLatencyMs()) + " ms average latency. " + cost;
    }

    private String normalizeOperator(String value) {
        String operator = required(value, "Alert operator").toUpperCase();
        if (!List.of("GT", "GTE", "LT", "LTE", "EQ").contains(operator)) {
            throw invalid("AI_ALERT_OPERATOR_INVALID", "Unsupported alert operator");
        }
        return operator;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_ANALYTICS_FIELD_REQUIRED", label + " is required");
        }
        return value.trim();
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private io.coreplatform.ai.application.exception.ProviderOperationException invalid(
            String code,
            String message
    ) {
        return new io.coreplatform.ai.application.exception.ProviderOperationException(code, message, 422);
    }
}
