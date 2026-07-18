package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ConnectionTestResult;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderHealth;
import io.coreplatform.ai.application.domain.ProviderStatus;
import io.coreplatform.ai.application.domain.ProviderType;
import io.coreplatform.ai.application.port.ModelDiscoveryPort;
import io.coreplatform.ai.application.port.ProviderProbePort;
import io.coreplatform.ai.application.port.ProviderRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.application.port.SecretCipherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    private ProviderRepository repository;

    @Mock
    private ProviderProbePort probePort;

    @Mock
    private SecretCipherPort secretCipher;

    @Mock
    private RequestContextPort requestContext;

    @Mock
    private ModelDiscoveryPort modelDiscovery;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService(
                repository,
                probePort,
                secretCipher,
                requestContext,
                new CapabilityDetector(),
                modelDiscovery
        );
    }

    @Test
    void shouldKeepProviderSuccessfulWhenModelRegistrySynchronizationFails() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        ProviderData provider = new ProviderData(
                "provider",
                "local",
                "Local",
                null,
                ProviderType.OLLAMA,
                "http://localhost:11434",
                false,
                ProviderStatus.DRAFT,
                100,
                100,
                15,
                0,
                Set.of(),
                null,
                null,
                null,
                null,
                true,
                Map.of(),
                Map.of(),
                Set.of(),
                ProviderHealth.empty(),
                0,
                now,
                now,
                "test",
                "test"
        );
        DiscoveredModel model = new DiscoveredModel(
                "deepseek-embedding-v1",
                "DeepSeek Embedding",
                Set.of(),
                8_192
        );
        when(repository.findById("provider")).thenReturn(Optional.of(provider));
        when(probePort.probe(any())).thenReturn(new ProviderProbePort.ProbeResult(12, List.of(model)));
        when(requestContext.actor()).thenReturn("test");
        when(requestContext.traceId()).thenReturn("trace");
        doThrow(new IllegalStateException("registry unavailable"))
                .when(modelDiscovery)
                .synchronizeDiscovered(any(), any(), any(), any());

        ConnectionTestResult result = service.testConnection("provider");

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(ProviderStatus.AVAILABLE);
        assertThat(result.capabilities()).containsExactly(Capability.EMBEDDING);
        verify(repository).recordHealthSuccess(
                eq("provider"),
                eq(12L),
                any(Instant.class),
                eq("test")
        );
        verify(repository).updateStatus(
                eq("provider"),
                eq(ProviderStatus.AVAILABLE),
                eq(false),
                any(Instant.class),
                eq("test")
        );

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository, times(2)).addAudit(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(AuditEntry::action, AuditEntry::result)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MODEL_REGISTRY_SYNC", "FAILED"),
                        org.assertj.core.groups.Tuple.tuple("TEST_CONNECTION", "SUCCESS")
                );
    }
}
