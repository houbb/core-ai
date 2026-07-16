package io.coreplatform.ai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core")
public record CoreAiProperties(
        Security security,
        Crypto crypto,
        Provider provider
) {

    public CoreAiProperties {
        security = security == null ? new Security("local") : security;
        crypto = crypto == null ? new Crypto(null) : crypto;
        provider = provider == null ? new Provider(2_097_152, false, 300_000) : provider;
    }

    public record Security(String mode) {
    }

    public record Crypto(String masterKey) {
    }

    public record Provider(int maxResponseBytes, boolean healthCheckEnabled, long healthCheckDelayMs) {
    }
}
