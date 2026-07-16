package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.AnalyticsModels.AlertRule;
import io.coreplatform.ai.application.domain.AnalyticsModels.Budget;
import io.coreplatform.ai.application.domain.AnalyticsModels.Dashboard;
import io.coreplatform.ai.application.domain.AnalyticsModels.Evaluation;
import io.coreplatform.ai.application.domain.AnalyticsModels.Feedback;
import io.coreplatform.ai.application.domain.AnalyticsModels.TraceSpan;
import io.coreplatform.ai.application.service.AnalyticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return service.dashboard(from, to);
    }

    @GetMapping("/insight")
    public Map<String, String> insight(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return Map.of("insight", service.insight(from, to), "mode", "DETERMINISTIC");
    }

    @GetMapping("/traces/{traceId}")
    public List<TraceSpan> trace(@PathVariable String traceId) {
        return service.trace(traceId);
    }

    @PostMapping("/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public Evaluation evaluate(@Valid @RequestBody EvaluationRequest request) {
        return service.evaluate(
                request.targetType(),
                request.targetId(),
                request.evaluationType(),
                request.score(),
                request.judge(),
                request.dimensions(),
                request.comment()
        );
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback feedback(@Valid @RequestBody FeedbackRequest request) {
        return service.feedback(
                request.conversationId(),
                request.messageId(),
                request.resourceType(),
                request.resourceId(),
                request.rating(),
                request.comment()
        );
    }

    @GetMapping("/alerts")
    public List<AlertRule> alerts() {
        return service.alerts();
    }

    @PostMapping("/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule createAlert(@Valid @RequestBody AlertRequest request) {
        return saveAlert(null, request);
    }

    @PutMapping("/alerts/{id}")
    public AlertRule updateAlert(@PathVariable String id, @Valid @RequestBody AlertRequest request) {
        return saveAlert(id, request);
    }

    @GetMapping("/budgets")
    public List<Budget> budgets() {
        return service.budgets();
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    public Budget createBudget(@Valid @RequestBody BudgetRequest request) {
        return saveBudget(null, request);
    }

    @PutMapping("/budgets/{id}")
    public Budget updateBudget(@PathVariable String id, @Valid @RequestBody BudgetRequest request) {
        return saveBudget(id, request);
    }

    private AlertRule saveAlert(String id, AlertRequest request) {
        return service.saveAlert(
                id,
                request.name(),
                request.metricName(),
                request.operator(),
                request.threshold(),
                request.action(),
                request.scope(),
                request.enabled()
        );
    }

    private Budget saveBudget(String id, BudgetRequest request) {
        return service.saveBudget(
                id,
                request.ownerType(),
                request.ownerId(),
                request.periodType(),
                request.currency(),
                request.amount(),
                request.warningRatio(),
                request.limitAction(),
                request.enabled()
        );
    }

    public record EvaluationRequest(
            @NotBlank @Size(max = 40) String targetType,
            @NotBlank @Size(max = 100) String targetId,
            @NotBlank @Size(max = 40) String evaluationType,
            @DecimalMin("0") @DecimalMax("5") double score,
            @Size(max = 100) String judge,
            Map<String, Object> dimensions,
            @Size(max = 4000) String comment
    ) {
    }

    public record FeedbackRequest(
            @Size(max = 64) String conversationId,
            @Size(max = 64) String messageId,
            @Size(max = 40) String resourceType,
            @Size(max = 100) String resourceId,
            @Min(-1) @Max(5) int rating,
            @Size(max = 4000) String comment
    ) {
    }

    public record AlertRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String metricName,
            @NotBlank @Size(max = 20) String operator,
            double threshold,
            @NotBlank @Size(max = 100) String action,
            Map<String, Object> scope,
            boolean enabled
    ) {
    }

    public record BudgetRequest(
            @NotBlank @Size(max = 32) String ownerType,
            @NotBlank @Size(max = 100) String ownerId,
            @NotBlank @Size(max = 20) String periodType,
            @NotBlank @Size(max = 16) String currency,
            @NotNull @DecimalMin("0") BigDecimal amount,
            @DecimalMin("0.01") @DecimalMax("1") double warningRatio,
            @NotBlank @Size(max = 32) String limitAction,
            boolean enabled
    ) {
    }
}
