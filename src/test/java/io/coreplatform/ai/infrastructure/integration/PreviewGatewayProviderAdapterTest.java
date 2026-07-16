package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderRequest;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewGatewayProviderAdapterTest {

    @Test
    void shouldReturnDeterministicNonExecutingPreview() {
        ProviderResult result = new PreviewGatewayProviderAdapter().execute(new ProviderRequest(
                new Invocation(
                        "request", "trace", "chat", "chat-default", "hello",
                        Map.of(), true, false, "tester"
                ),
                null,
                30,
                1
        ));

        assertThat(result.executed()).isFalse();
        assertThat(result.mode()).isEqualTo("PREVIEW");
        assertThat(result.output()).contains("chat-default", "not configured");
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.cost()).isZero();
    }
}
