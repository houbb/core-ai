package io.coreplatform.ai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.prompt")
public record PromptRuntimeProperties(
        boolean renderLogContentEnabled,
        int renderLogMaxEntries,
        int maxChainDepth
) {

    public PromptRuntimeProperties {
        renderLogMaxEntries = renderLogMaxEntries <= 0 ? 200 : renderLogMaxEntries;
        maxChainDepth = maxChainDepth <= 0 ? 10 : maxChainDepth;
    }
}
