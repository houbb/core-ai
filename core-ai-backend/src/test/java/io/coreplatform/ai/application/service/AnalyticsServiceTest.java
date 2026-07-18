package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AnalyticsModels.Dashboard;
import io.coreplatform.ai.application.port.AnalyticsRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Test
    void shouldGenerateDeterministicInsightAndRejectReverseRanges() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        RequestContextPort context = mock(RequestContextPort.class);
        AnalyticsService service = new AnalyticsService(repository, context);
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T00:00:00Z");
        when(repository.dashboard(from, to)).thenReturn(new Dashboard(
                100, 99, 1, 0.99, 42.5, 1000, 500,
                BigDecimal.ZERO, 4.8, List.of(), List.of(), List.of()
        ));

        assertThat(service.insight(from, to))
                .contains("stable", "100 requests", "99.0% success", "No billable");
        assertThatThrownBy(() -> service.dashboard(to, from))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("start time");
    }
}
