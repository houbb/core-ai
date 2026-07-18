package io.coreplatform.ai.application.domain;

public record ScenePackage(
        int formatVersion,
        String code,
        int version,
        SceneConfiguration configuration
) {
}
