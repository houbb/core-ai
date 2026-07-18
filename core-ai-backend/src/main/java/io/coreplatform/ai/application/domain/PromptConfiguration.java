package io.coreplatform.ai.application.domain;

import java.util.List;

public record PromptConfiguration(
        String name,
        String description,
        String category,
        String sceneId,
        PromptVisibility visibility,
        String projectCode,
        String departmentCode,
        String systemPrompt,
        String userPrompt,
        String assistantPrompt,
        String changeLog,
        List<PromptVariable> variables,
        PromptOutputSchema outputSchema,
        List<PromptGuardrail> guardrails,
        List<PromptChainStep> chain
) {

    public PromptConfiguration {
        variables = variables == null ? List.of() : List.copyOf(variables);
        outputSchema = outputSchema == null ? PromptOutputSchema.empty() : outputSchema;
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
        chain = chain == null ? List.of() : List.copyOf(chain);
    }
}
