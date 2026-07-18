package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.ToolModels.ExecutorRequest;
import io.coreplatform.ai.application.domain.ToolModels.ExecutorResult;
import io.coreplatform.ai.application.domain.ToolModels.Status;
import io.coreplatform.ai.application.domain.ToolModels.Tool;
import io.coreplatform.ai.application.domain.ToolModels.Version;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeToolExecutionAdapterTest {

    private final SafeToolExecutionAdapter adapter = new SafeToolExecutionAdapter();

    @Test
    void shouldExecuteMockLocallyAndKeepExternalExecutorsInPreview() {
        ExecutorResult mock = adapter.execute(new ExecutorRequest(
                tool("mock"),
                version("MOCK", Map.of("response", Map.of("ok", true))),
                Map.of("name", "Codex"),
                "trace-1"
        ));
        assertThat(mock.executed()).isTrue();
        assertThat(mock.mode()).isEqualTo("LOCAL");
        assertThat(mock.output()).isEqualTo(Map.of("ok", true));

        ExecutorResult http = adapter.execute(new ExecutorRequest(
                tool("http"),
                version("HTTP", Map.of("endpoint", "https://example.invalid")),
                Map.of(),
                "trace-2"
        ));
        assertThat(http.executed()).isFalse();
        assertThat(http.mode()).isEqualTo("PREVIEW");
        assertThat(http.output().toString()).contains("External", "HTTP");
    }

    private Tool tool(String code) {
        Instant now = Instant.now();
        return new Tool(
                code, code, code, null, "TEST", "PLUGIN", null, "tester",
                Status.PUBLISHED, 1, 1, now, now, "tester", "tester"
        );
    }

    private Version version(String executor, Map<String, Object> config) {
        Instant now = Instant.now();
        return new Version(
                "version", "tool", 1, "{\"type\":\"object\"}", null,
                executor, config, List.of(), null, true, now, now, List.of(),
                now, now, "tester", "tester"
        );
    }
}
