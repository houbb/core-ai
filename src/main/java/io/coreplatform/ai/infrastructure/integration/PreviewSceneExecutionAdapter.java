package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.SceneExecutionResult;
import io.coreplatform.ai.application.domain.SceneExecutionResult.TraceStep;
import io.coreplatform.ai.application.domain.SceneWorkflowStep;
import io.coreplatform.ai.application.port.SceneExecutionPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PreviewSceneExecutionAdapter implements SceneExecutionPort {

    @Override
    public SceneExecutionResult execute(ExecutionRequest request) {
        ResolvedSceneModel primary = request.resolvedModels().stream()
                .filter(item -> !item.binding().fallback())
                .findFirst()
                .orElseThrow();
        ModelData model = primary.model();
        PromptRenderResult prompt = request.renderedPrompt();
        long estimatedInputTokens = prompt == null
                ? Math.max(1, (request.input().length() + 3L) / 4L)
                : prompt.estimatedTokens();
        ModelPricing pricing = model.currentPricing(Instant.now());
        BigDecimal estimatedCost = null;
        String currency = null;
        if (pricing != null && pricing.promptPrice() != null) {
            estimatedCost = pricing.promptPrice()
                    .multiply(BigDecimal.valueOf(estimatedInputTokens))
                    .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
            currency = pricing.currency();
        }

        List<TraceStep> trace = new ArrayList<>();
        trace.add(step(1, "INPUT", "Input", request.input().length() + " characters", "VALIDATED"));
        trace.add(step(
                2,
                "SCENE",
                request.scene().code(),
                "Version " + request.scene().version() + " · " + request.scene().category(),
                "RESOLVED"
        ));
        trace.add(step(
                3,
                "PROMPT",
                prompt == null
                        ? "No Prompt binding"
                        : prompt.promptCode(),
                prompt == null
                        ? "Prompt Runtime skipped"
                        : "Prompt version " + prompt.version()
                        + " · " + prompt.estimatedTokens() + " estimated tokens",
                prompt == null ? "SKIPPED" : "RENDERED"
        ));
        int order = 4;
        for (ResolvedSceneModel resolved : request.resolvedModels()) {
            trace.add(step(
                    order++,
                    "MODEL",
                    resolved.binding().modelAlias(),
                    resolved.model().displayName() + " · " + resolved.model().providerName(),
                    resolved.binding().fallback() ? "FALLBACK_READY" : "PRIMARY_READY"
            ));
        }
        for (SceneWorkflowStep workflowStep : request.scene().workflow()) {
            trace.add(step(
                    order++,
                    "WORKFLOW",
                    workflowStep.code(),
                    workflowStep.type() + " → " + workflowStep.reference(),
                    workflowStep.optional() ? "OPTIONAL_PREVIEW" : "PREVIEW"
            ));
        }
        trace.add(step(
                order,
                "OUTPUT",
                "Preview",
                "Real inference requires a SceneExecutionPort Gateway adapter",
                "PREVIEW"
        ));

        return new SceneExecutionResult(
                "PREVIEW",
                false,
                "Scene configuration validated. Real AI output is not generated in Preview mode.",
                request.scene().code(),
                request.scene().version(),
                primary.binding().modelAlias(),
                model.id(),
                model.displayName(),
                model.providerName(),
                prompt == null ? null : prompt.promptId(),
                prompt == null ? null : prompt.version(),
                model.providerLatencyMs() == null ? 0 : model.providerLatencyMs(),
                estimatedInputTokens,
                estimatedCost,
                currency,
                trace,
                Instant.now(),
                request.traceId()
        );
    }

    private TraceStep step(int order, String stage, String name, String detail, String status) {
        return new TraceStep(order, stage, name, detail, status);
    }
}
