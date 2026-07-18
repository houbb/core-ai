package io.coreplatform.ai.application.domain;

public record ModelParameters(
        Double temperature,
        Double topP,
        Double frequencyPenalty,
        Double presencePenalty,
        Integer maxOutputTokens,
        String reasoningEffort,
        Long seed
) {

    public static ModelParameters empty() {
        return new ModelParameters(null, null, null, null, null, null, null);
    }
}
