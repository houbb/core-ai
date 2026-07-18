package io.coreplatform.ai.api.response;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.SceneConfiguration;
import io.coreplatform.ai.application.domain.SceneExecutionResult;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.ScenePackage;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.domain.SceneTemplate;
import io.coreplatform.ai.application.domain.SceneVersion;
import io.coreplatform.ai.application.domain.SceneView;
import io.coreplatform.ai.application.domain.SceneWorkflowStep;
import io.coreplatform.ai.application.domain.SceneWorkflowStepType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SceneResponses {

    private SceneResponses() {
    }

    public static SceneResponse from(SceneView view) {
        Map<String, ModelData> resolved = new LinkedHashMap<>();
        for (ResolvedSceneModel item : view.resolvedModels()) {
            resolved.put(item.binding().modelAlias(), item.model());
        }
        var scene = view.scene();
        return new SceneResponse(
                scene.id(),
                scene.code(),
                scene.name(),
                scene.description(),
                scene.category(),
                scene.icon(),
                scene.status(),
                scene.enabled(),
                scene.version(),
                scene.recommended(),
                scene.lastTestedAt(),
                scene.lastTestedVersion(),
                scene.models().stream()
                        .map(binding -> ModelBindingResponse.from(binding, resolved.get(binding.modelAlias())))
                        .toList(),
                ParameterResponse.from(scene.parameters()),
                PromptResponse.from(scene.prompt()),
                scene.permissions().stream().map(PermissionResponse::from).toList(),
                scene.workflow().stream().map(WorkflowStepResponse::from).toList(),
                view.costTier(),
                scene.createTime(),
                scene.updateTime(),
                scene.createUser(),
                scene.updateUser()
        );
    }

    public record SceneResponse(
            String id,
            String code,
            String name,
            String description,
            String category,
            String icon,
            SceneStatus status,
            boolean enabled,
            int version,
            boolean recommended,
            Instant lastTestedAt,
            Integer lastTestedVersion,
            List<ModelBindingResponse> models,
            ParameterResponse parameters,
            PromptResponse prompt,
            List<PermissionResponse> permissions,
            List<WorkflowStepResponse> workflow,
            String costTier,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record ModelBindingResponse(
            String id,
            String modelAlias,
            int priority,
            boolean fallback,
            boolean enabled,
            boolean resolved,
            String modelId,
            String modelDisplayName,
            String providerName,
            Long latencyMs
    ) {

        static ModelBindingResponse from(SceneModelBinding binding, ModelData model) {
            return new ModelBindingResponse(
                    binding.id(),
                    binding.modelAlias(),
                    binding.priority(),
                    binding.fallback(),
                    binding.enabled(),
                    model != null,
                    model == null ? null : model.id(),
                    model == null ? null : model.displayName(),
                    model == null ? null : model.providerName(),
                    model == null ? null : model.providerLatencyMs()
            );
        }
    }

    public record ParameterResponse(
            Double temperature,
            Double topP,
            Integer maxOutputTokens,
            String reasoningEffort,
            boolean jsonMode,
            boolean streaming
    ) {

        static ParameterResponse from(SceneParameters parameters) {
            return new ParameterResponse(
                    parameters.temperature(),
                    parameters.topP(),
                    parameters.maxOutputTokens(),
                    parameters.reasoningEffort(),
                    parameters.jsonMode(),
                    parameters.streaming()
            );
        }
    }

    public record PromptResponse(String promptId, Integer promptVersion) {

        static PromptResponse from(ScenePromptBinding prompt) {
            return new PromptResponse(prompt.promptId(), prompt.promptVersion());
        }
    }

    public record PermissionResponse(ScenePermissionType type, String value) {

        static PermissionResponse from(ScenePermission permission) {
            return new PermissionResponse(permission.type(), permission.value());
        }
    }

    public record WorkflowStepResponse(
            String code,
            SceneWorkflowStepType type,
            String reference,
            boolean optional
    ) {

        static WorkflowStepResponse from(SceneWorkflowStep step) {
            return new WorkflowStepResponse(
                    step.code(),
                    step.type(),
                    step.reference(),
                    step.optional()
            );
        }
    }

    public record ConfigurationResponse(
            String name,
            String description,
            String category,
            String icon,
            boolean recommended,
            List<ConfigurationModelResponse> models,
            ParameterResponse parameters,
            PromptResponse prompt,
            List<PermissionResponse> permissions,
            List<WorkflowStepResponse> workflow
    ) {

        static ConfigurationResponse from(SceneConfiguration configuration) {
            return new ConfigurationResponse(
                    configuration.name(),
                    configuration.description(),
                    configuration.category(),
                    configuration.icon(),
                    configuration.recommended(),
                    configuration.models().stream()
                            .map(ConfigurationModelResponse::from)
                            .toList(),
                    ParameterResponse.from(configuration.parameters()),
                    PromptResponse.from(configuration.prompt()),
                    configuration.permissions().stream().map(PermissionResponse::from).toList(),
                    configuration.workflow().stream().map(WorkflowStepResponse::from).toList()
            );
        }
    }

    public record ConfigurationModelResponse(
            String modelAlias,
            int priority,
            boolean fallback,
            boolean enabled
    ) {

        static ConfigurationModelResponse from(SceneModelBinding binding) {
            return new ConfigurationModelResponse(
                    binding.modelAlias(),
                    binding.priority(),
                    binding.fallback(),
                    binding.enabled()
            );
        }
    }

    public record VersionResponse(
            String id,
            int version,
            ConfigurationResponse configuration,
            Instant createTime,
            String createUser
    ) {

        public static VersionResponse from(SceneVersion version) {
            return new VersionResponse(
                    version.id(),
                    version.version(),
                    ConfigurationResponse.from(version.configuration()),
                    version.createTime(),
                    version.createUser()
            );
        }
    }

    public record TemplateResponse(
            String id,
            String defaultCode,
            String templateName,
            String description,
            String category,
            String icon,
            boolean builtin,
            boolean recommended,
            ConfigurationResponse configuration
    ) {

        public static TemplateResponse from(SceneTemplate template) {
            return new TemplateResponse(
                    template.id(),
                    template.defaultCode(),
                    template.templateName(),
                    template.description(),
                    template.category(),
                    template.icon(),
                    template.builtin(),
                    template.recommended(),
                    ConfigurationResponse.from(template.configuration())
            );
        }
    }

    public record PackageResponse(
            int formatVersion,
            String code,
            int version,
            ConfigurationResponse configuration
    ) {

        public static PackageResponse from(ScenePackage scenePackage) {
            return new PackageResponse(
                    scenePackage.formatVersion(),
                    scenePackage.code(),
                    scenePackage.version(),
                    ConfigurationResponse.from(scenePackage.configuration())
            );
        }
    }

    public record ExecutionResponse(
            String mode,
            boolean executed,
            String output,
            String sceneCode,
            int sceneVersion,
            String modelAlias,
            String modelId,
            String modelDisplayName,
            String providerName,
            String promptId,
            Integer promptVersion,
            long latencyMs,
            Long estimatedInputTokens,
            BigDecimal estimatedCost,
            String currency,
            List<SceneExecutionResult.TraceStep> trace,
            Instant executeTime,
            String traceId
    ) {

        public static ExecutionResponse from(SceneExecutionResult result) {
            return new ExecutionResponse(
                    result.mode(),
                    result.executed(),
                    result.output(),
                    result.sceneCode(),
                    result.sceneVersion(),
                    result.modelAlias(),
                    result.modelId(),
                    result.modelDisplayName(),
                    result.providerName(),
                    result.promptId(),
                    result.promptVersion(),
                    result.latencyMs(),
                    result.estimatedInputTokens(),
                    result.estimatedCost(),
                    result.currency(),
                    result.trace(),
                    result.executeTime(),
                    result.traceId()
            );
        }
    }

    public record AuditResponse(
            String id,
            String action,
            String result,
            String detail,
            String traceId,
            Instant createTime,
            String createUser
    ) {

        public static AuditResponse from(AuditEntry entry) {
            return new AuditResponse(
                    entry.id(),
                    entry.action(),
                    entry.result(),
                    entry.detail(),
                    entry.traceId(),
                    entry.createTime(),
                    entry.createUser()
            );
        }
    }
}
