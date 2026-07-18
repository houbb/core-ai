package io.coreplatform.ai.application.domain;

import java.util.List;
import java.util.Set;

public record ConnectionTestResult(
        boolean success,
        ProviderStatus status,
        long latencyMs,
        int modelCount,
        Set<Capability> capabilities,
        List<ConnectionCheck> checks,
        String errorCode,
        String message,
        String userMessage,
        List<DiscoveredModel> models
) {

    public record ConnectionCheck(String name, boolean success, String detail) {
    }

    public static ConnectionTestResult success(
            ProviderStatus status,
            long latencyMs,
            List<DiscoveredModel> models,
            Set<Capability> capabilities
    ) {
        return new ConnectionTestResult(
                true,
                status,
                latencyMs,
                models.size(),
                capabilities,
                List.of(
                        new ConnectionCheck("endpoint", true, "OK"),
                        new ConnectionCheck("authentication", true, "OK"),
                        new ConnectionCheck("models", true, models.size() + " models"),
                        new ConnectionCheck("capability", true, capabilities.size() + " capabilities")
                ),
                null,
                "Connection successful",
                "连接成功",
                List.copyOf(models)
        );
    }

    public static ConnectionTestResult failure(
            ProviderStatus status,
            long latencyMs,
            String errorCode,
            String message,
            String userMessage
    ) {
        boolean endpointReachable = !"PROVIDER_ENDPOINT_INVALID".equals(errorCode)
                && !"PROVIDER_ENDPOINT_NOT_FOUND".equals(errorCode)
                && !"PROVIDER_NETWORK_ERROR".equals(errorCode)
                && !"PROVIDER_TIMEOUT".equals(errorCode);
        return new ConnectionTestResult(
                false,
                status,
                latencyMs,
                0,
                Set.of(),
                List.of(
                        new ConnectionCheck("endpoint", endpointReachable, message),
                        new ConnectionCheck("authentication", false, userMessage)
                ),
                errorCode,
                message,
                userMessage,
                List.of()
        );
    }
}
