package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;

public record PromptVersionData(
        String id,
        String promptId,
        int version,
        String systemPrompt,
        String userPrompt,
        String assistantPrompt,
        String changeLog,
        List<PromptVariable> variables,
        PromptOutputSchema outputSchema,
        List<PromptGuardrail> guardrails,
        List<PromptChainStep> chain,
        boolean testsPassed,
        Instant lastTestedTime,
        Instant publishedTime,
        Instant createTime,
        Instant updateTime,
        String createUser,
        String updateUser
) {

    public PromptVersionData {
        variables = variables == null ? List.of() : List.copyOf(variables);
        outputSchema = outputSchema == null ? PromptOutputSchema.empty() : outputSchema;
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
        chain = chain == null ? List.of() : List.copyOf(chain);
    }
}
