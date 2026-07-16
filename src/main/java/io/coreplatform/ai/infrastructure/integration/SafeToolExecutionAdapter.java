package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.ToolModels.ExecutorRequest;
import io.coreplatform.ai.application.domain.ToolModels.ExecutorResult;
import io.coreplatform.ai.application.port.ToolExecutionPort;

import java.util.LinkedHashMap;
import java.util.Map;

public class SafeToolExecutionAdapter implements ToolExecutionPort {

    @Override
    public ExecutorResult execute(ExecutorRequest request) {
        long started = System.nanoTime();
        String executor = request.version().executorType().toUpperCase();
        if ("MOCK".equals(executor)) {
            Object configured = request.version().executorConfig().get("response");
            Object output = configured == null
                    ? Map.of("ok", true, "echo", request.input())
                    : configured;
            return new ExecutorResult(
                    true,
                    "LOCAL",
                    output,
                    elapsed(started),
                    Map.of("executor", "MOCK")
            );
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("executed", false);
        preview.put("executor", executor);
        preview.put("tool", request.tool().code());
        preview.put("reason", "External Tool adapter is not configured; core validation completed");
        Object endpoint = request.version().executorConfig().get("endpoint");
        if (endpoint != null) {
            preview.put("endpoint", endpoint);
        }
        return new ExecutorResult(
                false,
                "PREVIEW",
                preview,
                elapsed(started),
                Map.of("externalAdapterRequired", true)
        );
    }

    private long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }
}
