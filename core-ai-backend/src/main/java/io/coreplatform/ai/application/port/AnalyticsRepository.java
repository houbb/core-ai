package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AnalyticsModels.AlertRule;
import io.coreplatform.ai.application.domain.AnalyticsModels.Budget;
import io.coreplatform.ai.application.domain.AnalyticsModels.Dashboard;
import io.coreplatform.ai.application.domain.AnalyticsModels.Evaluation;
import io.coreplatform.ai.application.domain.AnalyticsModels.Feedback;
import io.coreplatform.ai.application.domain.AnalyticsModels.TraceSpan;

import java.time.Instant;
import java.util.List;

public interface AnalyticsRepository {

    Dashboard dashboard(Instant from, Instant to);

    Evaluation insertEvaluation(Evaluation evaluation);

    Feedback insertFeedback(Feedback feedback);

    AlertRule saveAlert(AlertRule rule);

    List<AlertRule> findAlerts();

    Budget saveBudget(Budget budget);

    List<Budget> findBudgets();

    List<TraceSpan> findTrace(String traceId);
}
