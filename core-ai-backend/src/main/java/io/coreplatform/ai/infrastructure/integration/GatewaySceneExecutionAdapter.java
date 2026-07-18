package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.SceneExecutionResult;
import io.coreplatform.ai.application.domain.SceneExecutionResult.TraceStep;
import io.coreplatform.ai.application.port.GatewayInvocationPort;
import io.coreplatform.ai.application.port.SceneExecutionPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class GatewaySceneExecutionAdapter implements SceneExecutionPort {

    private final GatewayInvocationPort gateway;

    public GatewaySceneExecutionAdapter(GatewayInvocationPort gateway) {
        this.gateway = gateway;
    }

    @Override
    public SceneExecutionResult execute(ExecutionRequest request) {
        ResolvedSceneModel primary = request.resolvedModels().stream()
                .filter(item -> !item.binding().fallback())
                .findFirst()
                .orElseThrow();
        PromptRenderResult prompt = request.renderedPrompt();
        String input = prompt == null ? request.input() : prompt.combinedPrompt();
        InvocationResult result = gateway.invoke(new Invocation(
                UUID.randomUUID().toString(),
                request.traceId(),
                request.scene().code(),
                primary.binding().modelAlias(),
                input,
                request.variables(),
                !request.testMode(),
                request.scene().parameters().streaming(),
                null
        ));
        List<TraceStep> trace = new ArrayList<>();
        int order = 1;
        trace.add(new TraceStep(order++, "INPUT", "Input",
                request.input().length() + " characters", "VALIDATED"));
        trace.add(new TraceStep(order++, "SCENE", request.scene().code(),
                "Gateway is the unified execution entry", "ROUTED"));
        if (prompt != null) {
            trace.add(new TraceStep(order++, "PROMPT", prompt.promptCode(),
                    "Prompt version " + prompt.version(), "RENDERED"));
        }
        for (ResolvedSceneModel resolved : request.resolvedModels()) {
            trace.add(new TraceStep(
                    order++,
                    "MODEL",
                    resolved.binding().modelAlias(),
                    resolved.model().displayName() + " · " + resolved.model().providerName(),
                    resolved.binding().fallback() ? "FALLBACK_READY" : "PRIMARY_READY"
            ));
        }
        for (var workflow : request.scene().workflow()) {
            trace.add(new TraceStep(
                    order++,
                    "WORKFLOW",
                    workflow.code(),
                    workflow.type() + " -> " + workflow.reference(),
                    workflow.optional() ? "OPTIONAL_PREVIEW" : "PREVIEW"
            ));
        }
        for (Map<String, Object> step : result.trace()) {
            trace.add(new TraceStep(
                    order++,
                    String.valueOf(step.getOrDefault("stage", "GATEWAY")),
                    String.valueOf(step.getOrDefault("name", "Gateway")),
                    String.valueOf(step.getOrDefault("detail", "")),
                    String.valueOf(step.getOrDefault("status", "SUCCESS"))
            ));
        }
        BigDecimal cost = result.cost();
        String currency = result.currency();
        if ((cost == null || cost.signum() == 0) && !result.executed()) {
            ModelPricing pricing = primary.model().currentPricing(Instant.now());
            if (pricing != null && pricing.promptPrice() != null) {
                cost = pricing.promptPrice()
                        .multiply(BigDecimal.valueOf(result.inputTokens()))
                        .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
                currency = pricing.currency();
            }
        }
        return new SceneExecutionResult(
                result.mode(),
                result.executed(),
                result.output(),
                request.scene().code(),
                request.scene().version(),
                primary.binding().modelAlias(),
                primary.model().id(),
                primary.model().displayName(),
                primary.model().providerName(),
                prompt == null ? null : prompt.promptId(),
                prompt == null ? null : prompt.version(),
                result.latencyMs(),
                result.inputTokens(),
                cost,
                currency,
                trace,
                result.completedAt(),
                result.traceId()
        );
    }
}
