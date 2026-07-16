package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.PromptRequests.ChainStepRequest;
import io.coreplatform.ai.api.request.PromptRequests.CreatePromptRequest;
import io.coreplatform.ai.api.request.PromptRequests.GuardrailRequest;
import io.coreplatform.ai.api.request.PromptRequests.OutputSchemaRequest;
import io.coreplatform.ai.api.request.PromptRequests.UpdatePromptRequest;
import io.coreplatform.ai.api.request.PromptRequests.VariableRequest;
import io.coreplatform.ai.application.domain.PromptChainStep;
import io.coreplatform.ai.application.domain.PromptConfiguration;
import io.coreplatform.ai.application.domain.PromptGuardrail;
import io.coreplatform.ai.application.domain.PromptOutputSchema;
import io.coreplatform.ai.application.domain.PromptVariable;

import java.util.List;

final class PromptRequestMapper {

    private PromptRequestMapper() {
    }

    static PromptConfiguration configuration(CreatePromptRequest request) {
        return configuration(
                request.name(),
                request.description(),
                request.category(),
                request.sceneId(),
                request.visibility(),
                request.projectCode(),
                request.departmentCode(),
                request.systemPrompt(),
                request.userPrompt(),
                request.assistantPrompt(),
                request.changeLog(),
                request.variables(),
                request.outputSchema(),
                request.guardrails(),
                request.chain()
        );
    }

    static PromptConfiguration configuration(UpdatePromptRequest request) {
        return configuration(
                request.name(),
                request.description(),
                request.category(),
                request.sceneId(),
                request.visibility(),
                request.projectCode(),
                request.departmentCode(),
                request.systemPrompt(),
                request.userPrompt(),
                request.assistantPrompt(),
                request.changeLog(),
                request.variables(),
                request.outputSchema(),
                request.guardrails(),
                request.chain()
        );
    }

    private static PromptConfiguration configuration(
            String name,
            String description,
            String category,
            String sceneId,
            io.coreplatform.ai.application.domain.PromptVisibility visibility,
            String projectCode,
            String departmentCode,
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            String changeLog,
            List<VariableRequest> variables,
            OutputSchemaRequest outputSchema,
            List<GuardrailRequest> guardrails,
            List<ChainStepRequest> chain
    ) {
        return new PromptConfiguration(
                name,
                description,
                category,
                sceneId,
                visibility,
                projectCode,
                departmentCode,
                systemPrompt,
                userPrompt,
                assistantPrompt,
                changeLog,
                variables == null ? List.of() : variables.stream().map(item ->
                        new PromptVariable(
                                null,
                                null,
                                item.name(),
                                item.type(),
                                item.required(),
                                item.defaultValue(),
                                item.description(),
                                null,
                                null,
                                null,
                                null
                        )
                ).toList(),
                outputSchema == null
                        ? PromptOutputSchema.empty()
                        : new PromptOutputSchema(
                        null,
                        null,
                        outputSchema.schemaJson(),
                        outputSchema.strictMode(),
                        null,
                        null,
                        null,
                        null
                ),
                guardrails == null ? List.of() : guardrails.stream().map(item ->
                        new PromptGuardrail(
                                null,
                                null,
                                item.type(),
                                item.phase(),
                                item.configJson(),
                                item.enabled(),
                                null,
                                null,
                                null,
                                null
                        )
                ).toList(),
                chain == null ? List.of() : chain.stream().map(item ->
                        new PromptChainStep(item.reference(), item.version(), item.optional())
                ).toList()
        );
    }
}
