package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.port.SceneExecutionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewSceneExecutionAdapterTest {

    private final PreviewSceneExecutionAdapter adapter = new PreviewSceneExecutionAdapter();

    @Test
    void shouldReturnExplicitPreviewTraceAndCostEstimate() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        SceneModelBinding binding = new SceneModelBinding(
                "binding", "scene", "chat-default", 10, false, true,
                now, now, "test", "test"
        );
        SceneData scene = new SceneData(
                "scene", "chat", "Chat", null, "CONVERSATION", "💬",
                SceneStatus.TESTING, false, 1, true, null, null,
                List.of(binding),
                SceneParameters.defaults(),
                new ScenePromptBinding("prompt-chat", 2),
                List.of(new ScenePermission(
                        "permission", "scene", ScenePermissionType.EVERYONE, "*",
                        now, now, "test", "test"
                )),
                List.of(),
                now, now, "test", "test"
        );
        ModelPricing pricing = new ModelPricing(
                "price", "model", "USD",
                new BigDecimal("2.00"), new BigDecimal("8.00"), null, null,
                now.minusSeconds(60), "MANUAL", null, now, "test"
        );
        ModelData model = new ModelData(
                "model", "provider", "provider", "Provider", true, 30L,
                "gpt-4o", "GPT-4o", ModelCategory.CHAT, null,
                ModelStatus.ENABLED, true, true, true, false,
                128_000, null, 8_000, 4_000, false,
                Set.of(Capability.CHAT), Map.of(), ModelParameters.empty(),
                List.of(pricing), List.of(), Set.of(),
                now, now, now, "test", "test"
        );

        var result = adapter.execute(new SceneExecutionPort.ExecutionRequest(
                scene,
                List.of(new ResolvedSceneModel(binding, model)),
                "hello preview",
                Map.of(),
                new PromptRenderResult(
                        "prompt-id",
                        "prompt-chat",
                        2,
                        "System",
                        "hello preview",
                        null,
                        List.of(),
                        20,
                        5,
                        null,
                        false,
                        "SCENE"
                ),
                true,
                "trace"
        ));

        assertThat(result.mode()).isEqualTo("PREVIEW");
        assertThat(result.executed()).isFalse();
        assertThat(result.estimatedInputTokens()).isPositive();
        assertThat(result.estimatedCost()).isPositive();
        assertThat(result.promptId()).isEqualTo("prompt-id");
        assertThat(result.promptVersion()).isEqualTo(2);
        assertThat(result.trace())
                .extracting(item -> item.stage())
                .containsExactly("INPUT", "SCENE", "PROMPT", "MODEL", "OUTPUT");
    }
}
