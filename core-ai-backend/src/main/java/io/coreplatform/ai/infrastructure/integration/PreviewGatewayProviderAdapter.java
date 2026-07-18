package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.GatewayModels.ProviderRequest;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderResult;
import io.coreplatform.ai.application.port.GatewayProviderPort;

import java.math.BigDecimal;
import java.util.Map;

public class PreviewGatewayProviderAdapter implements GatewayProviderPort {

    @Override
    public ProviderResult execute(ProviderRequest request) {
        String input = request.invocation().input() == null ? "" : request.invocation().input();
        String target = request.route() == null
                ? request.invocation().aliasCode()
                : request.route().aliasCode();
        String output = "Gateway preview validated route " + target
                + ". External provider execution is not configured.";
        return new ProviderResult(
                false,
                "PREVIEW",
                output,
                Math.max(1, (input.length() + 3L) / 4L),
                Math.max(1, (output.length() + 3L) / 4L),
                0,
                BigDecimal.ZERO,
                null,
                request.route() == null ? null : request.route().providerId(),
                request.route() == null ? null : request.route().modelId(),
                Map.of("adapter", "preview", "attempt", request.attempt())
        );
    }
}
