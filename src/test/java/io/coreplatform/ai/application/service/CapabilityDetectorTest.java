package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityDetectorTest {

    private final CapabilityDetector detector = new CapabilityDetector();

    @Test
    void shouldDetectSpecializedAndMultimodalCapabilities() {
        assertThat(detector.detect("text-embedding-3-large", Set.of()))
                .containsExactly(Capability.EMBEDDING);
        assertThat(detector.detect("deepseek-embedding-v1", Set.of()))
                .containsExactly(Capability.EMBEDDING);
        assertThat(detector.detect("gpt-4o", Set.of()))
                .contains(Capability.CHAT, Capability.VISION);
        assertThat(detector.detect("deepseek-r1", Set.of()))
                .contains(Capability.CHAT, Capability.REASONING);
        assertThat(detector.detect("flux-1", Set.of()))
                .containsExactly(Capability.IMAGE);
    }

    @Test
    void shouldAggregateCapabilitiesAcrossDiscoveredModels() {
        Set<Capability> result = detector.aggregate(List.of(
                new DiscoveredModel("gpt-4o", "GPT-4o", Set.of(), null),
                new DiscoveredModel("text-embedding-3-large", "Embedding", Set.of(), null),
                new DiscoveredModel("whisper-1", "Whisper", Set.of(), null)
        ));

        assertThat(result).contains(
                Capability.CHAT,
                Capability.VISION,
                Capability.EMBEDDING,
                Capability.AUDIO
        );
    }
}
