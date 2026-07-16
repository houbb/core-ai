package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.ResolvedSceneModel;
import io.coreplatform.ai.application.domain.SceneConfiguration;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneExecutionResult;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.ScenePackage;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneSearchCriteria;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.domain.SceneTemplate;
import io.coreplatform.ai.application.domain.SceneVersion;
import io.coreplatform.ai.application.domain.SceneView;
import io.coreplatform.ai.application.domain.SceneWorkflowStep;
import io.coreplatform.ai.application.domain.SceneWorkflowStepType;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ModelRepository;
import io.coreplatform.ai.application.port.PromptReferencePort;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.application.port.SceneExecutionPort;
import io.coreplatform.ai.application.port.ScenePermissionPort;
import io.coreplatform.ai.application.port.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SceneService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,99}");
    private static final Pattern STEP_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");
    private static final Set<String> REASONING_EFFORTS = Set.of("low", "medium", "high");

    private final SceneRepository repository;
    private final ModelRepository modelRepository;
    private final SceneExecutionPort executionPort;
    private final ScenePermissionPort permissionPort;
    private final PromptReferencePort promptReferencePort;
    private final RequestContextPort requestContext;

    public SceneService(
            SceneRepository repository,
            ModelRepository modelRepository,
            SceneExecutionPort executionPort,
            ScenePermissionPort permissionPort,
            PromptReferencePort promptReferencePort,
            RequestContextPort requestContext
    ) {
        this.repository = repository;
        this.modelRepository = modelRepository;
        this.executionPort = executionPort;
        this.permissionPort = permissionPort;
        this.promptReferencePort = promptReferencePort;
        this.requestContext = requestContext;
    }

    @Transactional
    public SceneView create(String code, SceneConfiguration configuration) {
        String normalizedCode = normalizeCode(code);
        if (repository.existsByCode(normalizedCode)) {
            throw conflict("AI_SCENE_CODE_EXISTS", "Scene code already exists");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        SceneConfiguration normalized = normalizeConfiguration(id, normalizedCode, configuration, now, actor);
        SceneData scene = new SceneData(
                id,
                normalizedCode,
                normalized.name(),
                normalized.description(),
                normalized.category(),
                normalized.icon(),
                SceneStatus.DRAFT,
                false,
                1,
                normalized.recommended(),
                null,
                null,
                normalized.models(),
                normalized.parameters(),
                normalized.prompt(),
                normalized.permissions(),
                normalized.workflow(),
                now,
                now,
                actor,
                actor
        );
        SceneData saved = repository.insert(scene, now, actor);
        audit(saved.id(), "CREATE", "SUCCESS", "{\"version\":1}", actor, now);
        return view(saved, new HashMap<>());
    }

    @Transactional
    public SceneView update(String id, SceneConfiguration configuration) {
        SceneData current = requireScene(id);
        if (current.status() != SceneStatus.DRAFT && current.status() != SceneStatus.TESTING) {
            throw conflict(
                    "AI_SCENE_EDIT_STATUS_INVALID",
                    "Only Draft or Testing scenes can be edited"
            );
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        SceneConfiguration normalized = normalizeConfiguration(id, current.code(), configuration, now, actor);
        SceneData next = new SceneData(
                current.id(),
                current.code(),
                normalized.name(),
                normalized.description(),
                normalized.category(),
                normalized.icon(),
                current.status(),
                current.enabled(),
                current.version(),
                normalized.recommended(),
                null,
                null,
                normalized.models(),
                normalized.parameters(),
                normalized.prompt(),
                normalized.permissions(),
                normalized.workflow(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        SceneData saved = repository.update(next, now, actor);
        audit(id, "UPDATE", "SUCCESS", "{\"version\":" + current.version() + "}", actor, now);
        return view(saved, new HashMap<>());
    }

    public List<SceneView> search(SceneSearchCriteria criteria) {
        Map<String, List<ModelData>> aliasCache = new HashMap<>();
        return repository.search(criteria).stream()
                .map(scene -> view(scene, aliasCache))
                .toList();
    }

    public SceneView get(String id) {
        return view(requireScene(id), new HashMap<>());
    }

    public List<AuditEntry> audit(String id) {
        requireScene(id);
        return repository.findAudit(id);
    }

    @Transactional
    public SceneView transition(String id, SceneStatus target) {
        SceneData current = requireScene(id);
        if (target == null) {
            throw unprocessable("AI_SCENE_STATUS_REQUIRED", "Target Scene status is required");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        int nextVersion = current.version();
        boolean enabled = false;

        switch (current.status()) {
            case DRAFT -> {
                if (target == SceneStatus.TESTING) {
                    validateResolvable(current);
                } else if (target != SceneStatus.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
            }
            case TESTING -> {
                if (target == SceneStatus.PUBLISHED) {
                    validateResolvable(current);
                    if (current.lastTestedVersion() == null
                            || current.lastTestedVersion() != current.version()) {
                        throw conflict(
                                "AI_SCENE_TEST_REQUIRED",
                                "Test the current Scene version successfully before publishing"
                        );
                    }
                    if (!repository.versionExists(current.id(), current.version())) {
                        repository.addVersion(new SceneVersion(
                                UUID.randomUUID().toString(),
                                current.id(),
                                current.version(),
                                current.configuration(),
                                now,
                                actor
                        ), now, actor);
                    }
                    enabled = true;
                } else if (target != SceneStatus.DRAFT) {
                    invalidTransition(current.status(), target);
                }
            }
            case PUBLISHED -> {
                if (target != SceneStatus.DISABLED) {
                    invalidTransition(current.status(), target);
                }
            }
            case DISABLED -> {
                if (target == SceneStatus.DRAFT) {
                    nextVersion = Math.max(current.version(), repository.maxVersion(id)) + 1;
                } else if (target != SceneStatus.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
            }
            case ARCHIVED -> invalidTransition(current.status(), target);
        }

        repository.updateLifecycle(id, target, enabled, nextVersion, now, actor);
        audit(
                id,
                "STATUS_CHANGE",
                "SUCCESS",
                "{\"from\":\"" + current.status() + "\",\"to\":\"" + target
                        + "\",\"version\":" + nextVersion + "}",
                actor,
                now
        );
        return get(id);
    }

    @Transactional
    public SceneExecutionResult test(String id, String input, Map<String, Object> variables) {
        SceneData scene = requireScene(id);
        if (scene.status() != SceneStatus.TESTING) {
            throw conflict("AI_SCENE_TEST_STATUS_INVALID", "Scene must be in Testing status");
        }
        List<ResolvedSceneModel> resolved = validateResolvable(scene);
        String normalizedInput = normalizeInput(input);
        Map<String, Object> runtimeVariables = promptVariables(normalizedInput, variables);
        PromptRenderResult renderedPrompt = renderPrompt(scene, runtimeVariables);
        SceneExecutionResult result = executionPort.execute(new SceneExecutionPort.ExecutionRequest(
                scene,
                resolved,
                normalizedInput,
                runtimeVariables,
                renderedPrompt,
                true,
                requestContext.traceId()
        ));
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.markTested(id, scene.version(), now, actor);
        audit(
                id,
                "TEST",
                "SUCCESS",
                "{\"mode\":\"" + safeJson(result.mode()) + "\",\"version\":" + scene.version() + "}",
                actor,
                now
        );
        return result;
    }

    public List<SceneVersion> versions(String id) {
        requireScene(id);
        return repository.findVersions(id);
    }

    @Transactional
    public SceneView rollback(String id, int version) {
        SceneData current = requireScene(id);
        if (current.status() == SceneStatus.PUBLISHED) {
            throw conflict("AI_SCENE_DISABLE_BEFORE_ROLLBACK", "Disable the Scene before rollback");
        }
        if (current.status() == SceneStatus.ARCHIVED) {
            throw conflict("AI_SCENE_ARCHIVED", "Archived Scene cannot be changed");
        }
        SceneVersion snapshot = repository.findVersion(id, version)
                .orElseThrow(() -> notFound("AI_SCENE_VERSION_NOT_FOUND", "Scene version not found"));
        int nextVersion = Math.max(current.version(), repository.maxVersion(id)) + 1;
        String actor = requestContext.actor();
        Instant now = Instant.now();
        SceneConfiguration normalized = normalizeConfiguration(
                id,
                current.code(),
                snapshot.configuration(),
                now,
                actor
        );
        repository.update(new SceneData(
                current.id(),
                current.code(),
                normalized.name(),
                normalized.description(),
                normalized.category(),
                normalized.icon(),
                SceneStatus.DRAFT,
                false,
                nextVersion,
                normalized.recommended(),
                null,
                null,
                normalized.models(),
                normalized.parameters(),
                normalized.prompt(),
                normalized.permissions(),
                normalized.workflow(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        ), now, actor);
        repository.updateLifecycle(id, SceneStatus.DRAFT, false, nextVersion, now, actor);
        audit(
                id,
                "ROLLBACK",
                "SUCCESS",
                "{\"sourceVersion\":" + version + ",\"newVersion\":" + nextVersion + "}",
                actor,
                now
        );
        return get(id);
    }

    public ScenePackage exportScene(String id) {
        SceneData scene = requireScene(id);
        return new ScenePackage(1, scene.code(), scene.version(), portable(scene.configuration()));
    }

    @Transactional
    public SceneView importScene(ScenePackage scenePackage) {
        if (scenePackage == null || scenePackage.formatVersion() != 1) {
            throw unprocessable("AI_SCENE_PACKAGE_INVALID", "Scene package format version must be 1");
        }
        SceneView imported = create(scenePackage.code(), scenePackage.configuration());
        Instant now = Instant.now();
        audit(
                imported.scene().id(),
                "IMPORT",
                "SUCCESS",
                "{\"formatVersion\":1}",
                requestContext.actor(),
                now
        );
        return imported;
    }

    public List<SceneTemplate> templates() {
        return repository.findTemplates();
    }

    @Transactional
    public SceneView instantiateTemplate(String templateId, String code, String name) {
        SceneTemplate template = requireTemplate(templateId);
        SceneConfiguration configuration = template.configuration();
        if (name != null && !name.isBlank()) {
            configuration = new SceneConfiguration(
                    name.trim(),
                    configuration.description(),
                    configuration.category(),
                    configuration.icon(),
                    configuration.recommended(),
                    configuration.models(),
                    configuration.parameters(),
                    configuration.prompt(),
                    configuration.permissions(),
                    configuration.workflow()
            );
        }
        SceneView created = create(
                code == null || code.isBlank() ? template.defaultCode() : code,
                configuration
        );
        Instant now = Instant.now();
        audit(
                created.scene().id(),
                "CREATE_FROM_TEMPLATE",
                "SUCCESS",
                "{\"templateId\":\"" + template.id() + "\"}",
                requestContext.actor(),
                now
        );
        return created;
    }

    @Transactional
    public SceneTemplate saveAsTemplate(String sceneId, String templateName, String defaultCode) {
        SceneData scene = requireScene(sceneId);
        if (templateName == null || templateName.isBlank() || templateName.trim().length() > 200) {
            throw unprocessable(
                    "AI_SCENE_TEMPLATE_NAME_INVALID",
                    "Template name is required and max 200 chars"
            );
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String normalizedDefaultCode = normalizeCode(defaultCode);
        boolean duplicate = repository.findTemplates().stream().anyMatch(template ->
                template.defaultCode().equals(normalizedDefaultCode)
                        && template.templateName().equalsIgnoreCase(templateName.trim())
        );
        if (duplicate) {
            throw conflict("AI_SCENE_TEMPLATE_EXISTS", "Scene template already exists");
        }
        SceneTemplate template = new SceneTemplate(
                UUID.randomUUID().toString(),
                normalizedDefaultCode,
                templateName.trim(),
                scene.description(),
                scene.category(),
                scene.icon(),
                false,
                scene.recommended(),
                portable(scene.configuration()),
                now,
                now,
                actor,
                actor
        );
        SceneTemplate saved = repository.saveTemplate(template, now, actor);
        audit(
                sceneId,
                "CREATE_TEMPLATE",
                "SUCCESS",
                "{\"templateId\":\"" + saved.id() + "\"}",
                actor,
                now
        );
        return saved;
    }

    @Transactional
    public void deleteTemplate(String templateId) {
        SceneTemplate template = requireTemplate(templateId);
        if (template.builtin()) {
            throw conflict("AI_SCENE_TEMPLATE_BUILTIN", "Builtin Scene templates cannot be deleted");
        }
        repository.deleteTemplate(templateId);
        Instant now = Instant.now();
        repository.addAudit(new AuditEntry(
                UUID.randomUUID().toString(),
                "AI_SCENE_TEMPLATE",
                templateId,
                "DELETE_TEMPLATE",
                "SUCCESS",
                "{\"templateName\":\"" + safeJson(template.templateName()) + "\"}",
                requestContext.traceId(),
                now,
                requestContext.actor()
        ));
    }

    public List<SceneView> catalog() {
        List<SceneView> result = new ArrayList<>();
        Map<String, List<ModelData>> aliasCache = new HashMap<>();
        for (SceneData scene : repository.search(new SceneSearchCriteria(
                null,
                null,
                SceneStatus.PUBLISHED,
                true,
                null
        ))) {
            if (!permissionPort.hasAccess(scene.permissions())) {
                continue;
            }
            try {
                List<ResolvedSceneModel> resolved = validateResolvable(scene, aliasCache);
                result.add(new SceneView(scene, resolved, costTier(resolved)));
            } catch (ProviderOperationException ignored) {
                // A published Scene with unavailable dependencies is hidden from the business catalog.
            }
        }
        return List.copyOf(result);
    }

    public SceneView runtimeScene(String code) {
        SceneData scene = requirePublished(code);
        requireAccess(scene);
        List<ResolvedSceneModel> resolved = validateResolvable(scene);
        return new SceneView(scene, resolved, costTier(resolved));
    }

    @Transactional
    public SceneExecutionResult execute(String code, String input, Map<String, Object> variables) {
        SceneData scene = requirePublished(code);
        requireAccess(scene);
        List<ResolvedSceneModel> resolved = validateResolvable(scene);
        String normalizedInput = normalizeInput(input);
        Map<String, Object> runtimeVariables = promptVariables(normalizedInput, variables);
        PromptRenderResult renderedPrompt = renderPrompt(scene, runtimeVariables);
        SceneExecutionResult result = executionPort.execute(new SceneExecutionPort.ExecutionRequest(
                scene,
                resolved,
                normalizedInput,
                runtimeVariables,
                renderedPrompt,
                false,
                requestContext.traceId()
        ));
        Instant now = Instant.now();
        audit(
                scene.id(),
                "EXECUTE",
                "SUCCESS",
                "{\"mode\":\"" + safeJson(result.mode()) + "\",\"executed\":" + result.executed() + "}",
                requestContext.actor(),
                now
        );
        return result;
    }

    private SceneConfiguration normalizeConfiguration(
            String sceneId,
            String sceneCode,
            SceneConfiguration configuration,
            Instant now,
            String actor
    ) {
        if (configuration == null) {
            throw unprocessable("AI_SCENE_CONFIGURATION_REQUIRED", "Scene configuration is required");
        }
        String name = trimRequired(configuration.name(), 200, "AI_SCENE_NAME_INVALID", "Scene name");
        String category = normalizeCategory(configuration.category());
        String description = trimOptional(configuration.description(), 4000);
        String icon = trimOptional(configuration.icon(), 40);
        List<SceneModelBinding> models = normalizeModels(
                sceneId,
                configuration.models(),
                now,
                actor
        );
        SceneParameters parameters = normalizeParameters(configuration.parameters());
        ScenePromptBinding prompt = normalizePrompt(configuration.prompt());
        List<ScenePermission> permissions = normalizePermissions(
                sceneId,
                configuration.permissions(),
                now,
                actor
        );
        List<SceneWorkflowStep> workflow = normalizeWorkflow(sceneCode, configuration.workflow());
        return new SceneConfiguration(
                name,
                description,
                category,
                icon,
                configuration.recommended(),
                models,
                parameters,
                prompt,
                permissions,
                workflow
        );
    }

    private List<SceneModelBinding> normalizeModels(
            String sceneId,
            List<SceneModelBinding> bindings,
            Instant now,
            String actor
    ) {
        if (bindings == null || bindings.isEmpty() || bindings.size() > 20) {
            throw unprocessable("AI_SCENE_MODEL_REQUIRED", "Scene must bind between 1 and 20 model aliases");
        }
        Set<String> aliases = new HashSet<>();
        List<SceneModelBinding> normalized = new ArrayList<>();
        int primaryCount = 0;
        for (SceneModelBinding binding : bindings) {
            String alias = normalizeAlias(binding.modelAlias());
            if (!aliases.add(alias)) {
                throw conflict("AI_SCENE_MODEL_ALIAS_DUPLICATE", "Scene model aliases must be unique");
            }
            if (binding.priority() < 0 || binding.priority() > 10000) {
                throw unprocessable("AI_SCENE_MODEL_PRIORITY_INVALID", "Model priority must be 0-10000");
            }
            if (binding.enabled() && !binding.fallback()) {
                primaryCount++;
            }
            normalized.add(new SceneModelBinding(
                    UUID.randomUUID().toString(),
                    sceneId,
                    alias,
                    binding.priority(),
                    binding.fallback(),
                    binding.enabled(),
                    now,
                    now,
                    actor,
                    actor
            ));
        }
        if (primaryCount != 1) {
            throw unprocessable(
                    "AI_SCENE_PRIMARY_MODEL_INVALID",
                    "Scene must have exactly one enabled primary model alias"
            );
        }
        return normalized.stream()
                .sorted(Comparator.comparing(SceneModelBinding::fallback)
                        .thenComparingInt(SceneModelBinding::priority)
                        .thenComparing(SceneModelBinding::modelAlias))
                .toList();
    }

    private SceneParameters normalizeParameters(SceneParameters parameters) {
        SceneParameters value = parameters == null ? SceneParameters.defaults() : parameters;
        validateRange(value.temperature(), 0, 2, "temperature");
        validateRange(value.topP(), 0, 1, "topP");
        if (value.maxOutputTokens() != null
                && (value.maxOutputTokens() < 1 || value.maxOutputTokens() > 10_000_000)) {
            throw unprocessable(
                    "AI_SCENE_PARAMETER_INVALID",
                    "maxOutputTokens must be between 1 and 10000000"
            );
        }
        String effort = value.reasoningEffort() == null || value.reasoningEffort().isBlank()
                ? null
                : value.reasoningEffort().trim().toLowerCase(Locale.ROOT);
        if (effort != null && !REASONING_EFFORTS.contains(effort)) {
            throw unprocessable(
                    "AI_SCENE_REASONING_EFFORT_INVALID",
                    "Reasoning effort must be low, medium or high"
            );
        }
        return new SceneParameters(
                value.temperature(),
                value.topP(),
                value.maxOutputTokens(),
                effort,
                value.jsonMode(),
                value.streaming()
        );
    }

    private ScenePromptBinding normalizePrompt(ScenePromptBinding prompt) {
        if (prompt == null || prompt.promptId() == null || prompt.promptId().isBlank()) {
            return ScenePromptBinding.empty();
        }
        String promptId = prompt.promptId().trim();
        if (promptId.length() > 100) {
            throw unprocessable("AI_SCENE_PROMPT_INVALID", "Prompt ID max length is 100");
        }
        if (prompt.promptVersion() != null && prompt.promptVersion() < 1) {
            throw unprocessable("AI_SCENE_PROMPT_VERSION_INVALID", "Prompt version must be positive");
        }
        return new ScenePromptBinding(promptId, prompt.promptVersion());
    }

    private List<ScenePermission> normalizePermissions(
            String sceneId,
            List<ScenePermission> permissions,
            Instant now,
            String actor
    ) {
        List<ScenePermission> source = permissions == null || permissions.isEmpty()
                ? List.of(new ScenePermission(
                null, null, ScenePermissionType.EVERYONE, "*", null, null, null, null
        ))
                : permissions;
        if (source.size() > 50) {
            throw unprocessable("AI_SCENE_PERMISSION_LIMIT", "Scene supports at most 50 permissions");
        }
        Set<String> keys = new HashSet<>();
        List<ScenePermission> normalized = new ArrayList<>();
        for (ScenePermission permission : source) {
            if (permission.type() == null) {
                throw unprocessable("AI_SCENE_PERMISSION_TYPE_REQUIRED", "Permission type is required");
            }
            String value = permission.type() == ScenePermissionType.EVERYONE
                    ? "*"
                    : trimRequired(
                    permission.value(),
                    200,
                    "AI_SCENE_PERMISSION_VALUE_INVALID",
                    "Permission value"
            ).toUpperCase(Locale.ROOT);
            String key = permission.type() + ":" + value;
            if (!keys.add(key)) {
                throw conflict("AI_SCENE_PERMISSION_DUPLICATE", "Scene permissions must be unique");
            }
            normalized.add(new ScenePermission(
                    UUID.randomUUID().toString(),
                    sceneId,
                    permission.type(),
                    value,
                    now,
                    now,
                    actor,
                    actor
            ));
        }
        return List.copyOf(normalized);
    }

    private List<SceneWorkflowStep> normalizeWorkflow(
            String sceneCode,
            List<SceneWorkflowStep> workflow
    ) {
        if (workflow == null || workflow.isEmpty()) {
            return List.of();
        }
        if (workflow.size() > 50) {
            throw unprocessable("AI_SCENE_WORKFLOW_LIMIT", "Scene supports at most 50 workflow steps");
        }
        Set<String> codes = new HashSet<>();
        List<SceneWorkflowStep> normalized = new ArrayList<>();
        for (SceneWorkflowStep step : workflow) {
            String code = step.code() == null
                    ? ""
                    : step.code().trim().toLowerCase(Locale.ROOT);
            if (!STEP_PATTERN.matcher(code).matches()) {
                throw unprocessable("AI_SCENE_WORKFLOW_CODE_INVALID", "Workflow step code is invalid");
            }
            if (!codes.add(code)) {
                throw conflict("AI_SCENE_WORKFLOW_CODE_DUPLICATE", "Workflow step codes must be unique");
            }
            if (step.type() == null) {
                throw unprocessable("AI_SCENE_WORKFLOW_TYPE_REQUIRED", "Workflow step type is required");
            }
            String reference = trimRequired(
                    step.reference(),
                    200,
                    "AI_SCENE_WORKFLOW_REFERENCE_INVALID",
                    "Workflow reference"
            );
            if (step.type() == SceneWorkflowStepType.MODEL_ALIAS) {
                reference = normalizeAlias(reference);
            } else if (step.type() == SceneWorkflowStepType.SCENE) {
                reference = normalizeCode(reference);
                if (reference.equals(sceneCode)) {
                    throw conflict("AI_SCENE_WORKFLOW_SELF_REFERENCE", "Scene cannot reference itself");
                }
            }
            normalized.add(new SceneWorkflowStep(code, step.type(), reference, step.optional()));
        }
        return List.copyOf(normalized);
    }

    private List<ResolvedSceneModel> validateResolvable(SceneData scene) {
        return validateResolvable(scene, new HashMap<>());
    }

    private List<ResolvedSceneModel> validateResolvable(
            SceneData scene,
            Map<String, List<ModelData>> aliasCache
    ) {
        List<ResolvedSceneModel> resolved = new ArrayList<>();
        for (SceneModelBinding binding : scene.models()) {
            if (!binding.enabled()) {
                continue;
            }
            List<ModelData> candidates = aliasCache.computeIfAbsent(
                    binding.modelAlias(),
                    modelRepository::resolveAlias
            );
            if (candidates.isEmpty()) {
                throw conflict(
                        "AI_SCENE_MODEL_ALIAS_UNRESOLVED",
                        "No enabled model resolves alias " + binding.modelAlias()
                );
            }
            resolved.add(new ResolvedSceneModel(binding, candidates.getFirst()));
        }
        if (resolved.stream().filter(item -> !item.binding().fallback()).count() != 1) {
            throw conflict(
                    "AI_SCENE_PRIMARY_MODEL_INVALID",
                    "Scene must resolve exactly one primary model alias"
            );
        }
        validateWorkflowReferences(scene, aliasCache);
        validatePromptReference(scene);
        return resolved.stream()
                .sorted(Comparator.comparing((ResolvedSceneModel item) -> item.binding().fallback())
                        .thenComparingInt(item -> item.binding().priority()))
                .toList();
    }

    private void validateWorkflowReferences(
            SceneData scene,
            Map<String, List<ModelData>> aliasCache
    ) {
        for (SceneWorkflowStep step : scene.workflow()) {
            boolean available = switch (step.type()) {
                case MODEL_ALIAS -> !aliasCache.computeIfAbsent(
                        step.reference(),
                        modelRepository::resolveAlias
                ).isEmpty();
                case SCENE -> repository.findByCode(step.reference())
                        .map(candidate -> candidate.status() == SceneStatus.PUBLISHED && candidate.enabled())
                        .orElse(false);
                case EXTERNAL -> true;
            };
            if (!available && !step.optional()) {
                throw conflict(
                        "AI_SCENE_WORKFLOW_REFERENCE_UNAVAILABLE",
                        "Workflow reference is unavailable: " + step.reference()
                );
            }
        }
    }

    private void validatePromptReference(SceneData scene) {
        if (scene.prompt().promptId() == null) {
            return;
        }
        promptReferencePort.resolvePublished(
                scene.prompt().promptId(),
                scene.prompt().promptVersion()
        );
    }

    private PromptRenderResult renderPrompt(
            SceneData scene,
            Map<String, Object> variables
    ) {
        if (scene.prompt().promptId() == null) {
            return null;
        }
        return promptReferencePort.renderPublished(
                scene.prompt().promptId(),
                scene.prompt().promptVersion(),
                variables
        );
    }

    private Map<String, Object> promptVariables(
            String input,
            Map<String, Object> variables
    ) {
        Map<String, Object> result = new HashMap<>();
        if (variables != null) {
            result.putAll(variables);
        }
        result.putIfAbsent("input", input);
        result.putIfAbsent("content", input);
        return Map.copyOf(result);
    }

    private SceneView view(SceneData scene, Map<String, List<ModelData>> aliasCache) {
        List<ResolvedSceneModel> resolved = new ArrayList<>();
        for (SceneModelBinding binding : scene.models()) {
            if (!binding.enabled()) {
                continue;
            }
            List<ModelData> models = aliasCache.computeIfAbsent(
                    binding.modelAlias(),
                    modelRepository::resolveAlias
            );
            if (!models.isEmpty()) {
                resolved.add(new ResolvedSceneModel(binding, models.getFirst()));
            }
        }
        resolved.sort(Comparator.comparing((ResolvedSceneModel item) -> item.binding().fallback())
                .thenComparingInt(item -> item.binding().priority()));
        return new SceneView(scene, resolved, costTier(resolved));
    }

    private String costTier(List<ResolvedSceneModel> resolved) {
        ModelData model = resolved.stream()
                .filter(item -> !item.binding().fallback())
                .map(ResolvedSceneModel::model)
                .findFirst()
                .orElse(null);
        if (model == null) {
            return "UNKNOWN";
        }
        ModelPricing price = model.currentPricing(Instant.now());
        if (price == null || (price.promptPrice() == null && price.completionPrice() == null)) {
            return "UNKNOWN";
        }
        BigDecimal total = decimal(price.promptPrice()).add(decimal(price.completionPrice()));
        if (total.compareTo(BigDecimal.ONE) <= 0) {
            return "CHEAP";
        }
        if (total.compareTo(BigDecimal.TEN) <= 0) {
            return "STANDARD";
        }
        return "PREMIUM";
    }

    private SceneConfiguration portable(SceneConfiguration configuration) {
        List<SceneModelBinding> models = configuration.models().stream()
                .map(binding -> new SceneModelBinding(
                        null,
                        null,
                        binding.modelAlias(),
                        binding.priority(),
                        binding.fallback(),
                        binding.enabled(),
                        null,
                        null,
                        null,
                        null
                ))
                .toList();
        List<ScenePermission> permissions = configuration.permissions().stream()
                .map(permission -> new ScenePermission(
                        null,
                        null,
                        permission.type(),
                        permission.value(),
                        null,
                        null,
                        null,
                        null
                ))
                .toList();
        return new SceneConfiguration(
                configuration.name(),
                configuration.description(),
                configuration.category(),
                configuration.icon(),
                configuration.recommended(),
                models,
                configuration.parameters(),
                configuration.prompt(),
                permissions,
                configuration.workflow()
        );
    }

    private SceneData requireScene(String id) {
        return repository.findById(id)
                .orElseThrow(() -> notFound("AI_SCENE_NOT_FOUND", "Scene not found"));
    }

    private SceneData requirePublished(String code) {
        SceneData scene = repository.findByCode(normalizeCode(code))
                .orElseThrow(() -> notFound("AI_SCENE_NOT_FOUND", "Scene not found"));
        if (scene.status() != SceneStatus.PUBLISHED || !scene.enabled()) {
            throw notFound("AI_SCENE_NOT_PUBLISHED", "Published Scene not found");
        }
        return scene;
    }

    private SceneTemplate requireTemplate(String id) {
        return repository.findTemplateById(id)
                .orElseThrow(() -> notFound("AI_SCENE_TEMPLATE_NOT_FOUND", "Scene template not found"));
    }

    private void requireAccess(SceneData scene) {
        if (!permissionPort.hasAccess(scene.permissions())) {
            throw new ProviderOperationException("AI_SCENE_ACCESS_DENIED", "Scene access denied", 403);
        }
    }

    private void validateRange(Double value, double minimum, double maximum, String field) {
        if (value != null && (value < minimum || value > maximum)) {
            throw unprocessable(
                    "AI_SCENE_PARAMETER_INVALID",
                    field + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private String normalizeInput(String input) {
        if (input == null || input.isBlank()) {
            throw unprocessable("AI_SCENE_INPUT_REQUIRED", "Scene input is required");
        }
        if (input.length() > 100_000) {
            throw unprocessable("AI_SCENE_INPUT_TOO_LARGE", "Scene input max length is 100000");
        }
        return input;
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw unprocessable(
                    "AI_SCENE_CODE_INVALID",
                    "Scene code must contain 2-100 lowercase letters, numbers, dots, underscores or hyphens"
            );
        }
        return normalized;
    }

    private String normalizeAlias(String alias) {
        String normalized = alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw unprocessable("AI_SCENE_MODEL_ALIAS_INVALID", "Model alias format is invalid");
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORY_PATTERN.matcher(normalized).matches()) {
            throw unprocessable(
                    "AI_SCENE_CATEGORY_INVALID",
                    "Scene category must contain 2-100 letters, numbers, dots, underscores or hyphens"
            );
        }
        return normalized;
    }

    private String trimRequired(String value, int max, String errorCode, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw unprocessable(errorCode, field + " is required and max " + max + " chars");
        }
        return value.trim();
    }

    private String trimOptional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw unprocessable("AI_SCENE_TEXT_TOO_LONG", "Scene text exceeds " + max + " chars");
        }
        return normalized;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void invalidTransition(SceneStatus current, SceneStatus target) {
        throw conflict(
                "AI_SCENE_STATUS_TRANSITION_INVALID",
                "Cannot transition Scene from " + current + " to " + target
        );
    }

    private void audit(
            String sceneId,
            String action,
            String result,
            String detail,
            String actor,
            Instant now
    ) {
        repository.addAudit(new AuditEntry(
                UUID.randomUUID().toString(),
                "AI_SCENE",
                sceneId,
                action,
                result,
                detail,
                requestContext.traceId(),
                now,
                actor
        ));
    }

    private String safeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ProviderOperationException notFound(String code, String message) {
        return new ProviderOperationException(code, message, 404);
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException unprocessable(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }
}
