package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelRecommendation;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ModelDiscoveryPort;
import io.coreplatform.ai.application.port.ModelRepository;
import io.coreplatform.ai.application.port.ProviderRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private ModelDiscoveryPort discoveryPort;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private RequestContextPort requestContext;

    private ModelService service;

    @BeforeEach
    void setUp() {
        service = new ModelService(modelRepository, discoveryPort, providerRepository, requestContext);
    }

    @Test
    void shouldRejectSkippingRegistrationLifecycleStep() {
        ModelData discovered = model(
                "discovered",
                ModelStatus.DISCOVERED,
                10,
                128_000,
                new BigDecimal("1.0")
        );
        when(modelRepository.findById("discovered")).thenReturn(Optional.of(discovered));

        assertThatThrownBy(() -> service.transition("discovered", ModelStatus.ENABLED))
                .isInstanceOfSatisfying(ProviderOperationException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("AI_MODEL_STATUS_TRANSITION_INVALID");
                    assertThat(exception.httpStatus()).isEqualTo(409);
                });
    }

    @Test
    void shouldRecommendModelsWithDeterministicCostSpeedAndContextOrdering() {
        ModelData cheap = model("cheap", ModelStatus.ENABLED, 200, 64_000, new BigDecimal("0.20"));
        ModelData fast = model("fast", ModelStatus.ENABLED, 20, 32_000, new BigDecimal("2.00"));
        ModelData large = model("large", ModelStatus.ENABLED, 80, 1_000_000, new BigDecimal("1.00"));
        when(modelRepository.search(any())).thenReturn(List.of(cheap, fast, large));

        List<ModelRecommendation> cheapest = service.recommend(
                Capability.CHAT,
                ModelService.RecommendationMode.CHEAPEST,
                3
        );
        List<ModelRecommendation> fastest = service.recommend(
                Capability.CHAT,
                ModelService.RecommendationMode.FASTEST,
                3
        );
        List<ModelRecommendation> context = service.recommend(
                Capability.CHAT,
                ModelService.RecommendationMode.LARGEST_CONTEXT,
                3
        );

        assertThat(cheapest).extracting(item -> item.model().id())
                .containsExactly("cheap", "large", "fast");
        assertThat(fastest).extracting(item -> item.model().id())
                .containsExactly("fast", "large", "cheap");
        assertThat(context).extracting(item -> item.model().id())
                .containsExactly("large", "cheap", "fast");
        assertThat(cheapest.getFirst().reason()).contains("USD");
    }

    @Test
    void shouldRejectTokenLimitsThatExceedContextWindow() {
        when(modelRepository.findById("model")).thenReturn(Optional.of(model(
                "model",
                ModelStatus.REGISTERED,
                10,
                1_000,
                BigDecimal.ONE
        )));

        assertThatThrownBy(() -> service.update(
                "model",
                new ModelService.UpdateModelCommand(
                        "Model",
                        ModelCategory.CHAT,
                        null,
                        1_000,
                        2_000,
                        500,
                        100,
                        true,
                        Set.of()
                )
        )).isInstanceOfSatisfying(ProviderOperationException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo("AI_MODEL_CONTEXT_LIMIT_INVALID")
        );
    }

    @Test
    void shouldRequireAtLeastOnePricingValue() {
        when(modelRepository.findById("model")).thenReturn(Optional.of(model(
                "model",
                ModelStatus.REGISTERED,
                10,
                1_000,
                BigDecimal.ONE
        )));

        assertThatThrownBy(() -> service.addPricing(
                "model",
                new ModelService.PricingCommand(
                        "USD",
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-07-16T00:00:00Z"),
                        null
                )
        )).isInstanceOfSatisfying(ProviderOperationException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo("AI_MODEL_PRICE_REQUIRED")
        );
    }

    @Test
    void shouldRejectNullCapabilityOverrideValues() {
        when(modelRepository.findById("model")).thenReturn(Optional.of(model(
                "model",
                ModelStatus.REGISTERED,
                10,
                1_000,
                BigDecimal.ONE
        )));
        Map<Capability, Boolean> overrides = new HashMap<>();
        overrides.put(Capability.CHAT, null);

        assertThatThrownBy(() -> service.updateCapabilities("model", overrides))
                .isInstanceOfSatisfying(ProviderOperationException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo("AI_MODEL_CAPABILITY_OVERRIDE_INVALID")
                );
    }

    private ModelData model(
            String id,
            ModelStatus status,
            long latency,
            int context,
            BigDecimal totalPrice
    ) {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        ModelPricing pricing = new ModelPricing(
                id + "-price",
                id,
                "USD",
                totalPrice.divide(BigDecimal.TWO),
                totalPrice.divide(BigDecimal.TWO),
                null,
                null,
                now.minusSeconds(60),
                "MANUAL",
                null,
                now,
                "test"
        );
        return new ModelData(
                id,
                "provider",
                "provider",
                "Provider",
                true,
                latency,
                id + "-remote",
                id,
                ModelCategory.CHAT,
                null,
                status,
                status == ModelStatus.ENABLED,
                true,
                "large".equals(id),
                "fast".equals(id),
                context,
                null,
                null,
                null,
                false,
                Set.of(Capability.CHAT, Capability.STREAMING),
                Map.of(),
                ModelParameters.empty(),
                List.of(pricing),
                List.of(),
                Set.of(),
                now,
                now,
                now,
                "test",
                "test"
        );
    }
}
