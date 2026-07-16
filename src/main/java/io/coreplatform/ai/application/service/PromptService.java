package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.PromptAbAssignment;
import io.coreplatform.ai.application.domain.PromptAbTest;
import io.coreplatform.ai.application.domain.PromptChainStep;
import io.coreplatform.ai.application.domain.PromptConfiguration;
import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.domain.PromptDiffLine;
import io.coreplatform.ai.application.domain.PromptEvaluationResult;
import io.coreplatform.ai.application.domain.PromptGuardrail;
import io.coreplatform.ai.application.domain.PromptGuardrailType;
import io.coreplatform.ai.application.domain.PromptOutputSchema;
import io.coreplatform.ai.application.domain.PromptReference;
import io.coreplatform.ai.application.domain.PromptRenderLog;
import io.coreplatform.ai.application.domain.PromptRenderedStage;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.PromptSearchCriteria;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptTestCase;
import io.coreplatform.ai.application.domain.PromptTestSuiteResult;
import io.coreplatform.ai.application.domain.PromptVariable;
import io.coreplatform.ai.application.domain.PromptVersionData;
import io.coreplatform.ai.application.domain.PromptView;
import io.coreplatform.ai.application.domain.PromptVisibility;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.PromptEvaluationPort;
import io.coreplatform.ai.application.port.PromptPermissionPort;
import io.coreplatform.ai.application.port.PromptReferencePort;
import io.coreplatform.ai.application.port.PromptRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.infrastructure.config.PromptRuntimeProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PromptService implements PromptReferencePort {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,99}");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final int MAX_TEMPLATE_CHARS = 200_000;

    private final PromptRepository repository;
    private final PromptPermissionPort permissionPort;
    private final PromptEvaluationPort evaluationPort;
    private final RequestContextPort requestContext;
    private final PromptTemplateRenderer renderer;
    private final PromptGuardrailEngine guardrailEngine;
    private final JsonSchemaValidator schemaValidator;
    private final PromptDiffService diffService;
    private final PromptRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public PromptService(
            PromptRepository repository,
            PromptPermissionPort permissionPort,
            PromptEvaluationPort evaluationPort,
            RequestContextPort requestContext,
            PromptTemplateRenderer renderer,
            PromptGuardrailEngine guardrailEngine,
            JsonSchemaValidator schemaValidator,
            PromptDiffService diffService,
            PromptRuntimeProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.permissionPort = permissionPort;
        this.evaluationPort = evaluationPort;
        this.requestContext = requestContext;
        this.renderer = renderer;
        this.guardrailEngine = guardrailEngine;
        this.schemaValidator = schemaValidator;
        this.diffService = diffService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PromptView create(String code, PromptConfiguration configuration) {
        String normalizedCode = normalizeCode(code);
        if (repository.existsByCode(normalizedCode)) {
            throw conflict("AI_PROMPT_CODE_EXISTS", "Prompt code already exists");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String promptId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        NormalizedPrompt normalized = normalize(
                promptId,
                normalizedCode,
                versionId,
                configuration,
                now,
                actor
        );
        PromptData prompt = new PromptData(
                promptId,
                normalizedCode,
                normalized.name(),
                normalized.description(),
                normalized.category(),
                normalized.sceneId(),
                PromptStatus.DRAFT,
                1,
                null,
                normalized.visibility(),
                normalized.projectCode(),
                normalized.departmentCode(),
                actor,
                now,
                now,
                actor,
                actor
        );
        repository.insertPrompt(prompt);
        repository.insertVersion(version(
                promptId,
                versionId,
                1,
                normalized,
                now,
                actor
        ));
        audit(promptId, "CREATE", "{\"version\":1}", now, actor);
        return getInternal(promptId);
    }

    @Transactional
    public PromptView update(String id, PromptConfiguration configuration) {
        PromptData current = requirePrompt(id);
        requireManage(current);
        if (current.status() == PromptStatus.ARCHIVED) {
            throw conflict("AI_PROMPT_ARCHIVED", "Archived Prompt cannot be edited");
        }
        PromptVersionData source = requireVersion(current.id(), current.currentVersion());
        int nextVersion = repository.maxVersion(id) + 1;
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String versionId = UUID.randomUUID().toString();
        NormalizedPrompt normalized = normalize(
                current.id(),
                current.code(),
                versionId,
                configuration,
                now,
                actor
        );
        repository.insertVersion(version(
                current.id(),
                versionId,
                nextVersion,
                normalized,
                now,
                actor
        ));
        repository.copyTestCases(source.id(), versionId, now, actor);
        PromptData next = metadata(current, normalized, PromptStatus.DRAFT, nextVersion, now, actor);
        repository.updatePrompt(
                next,
                PromptStatus.DRAFT,
                nextVersion,
                current.publishedVersion(),
                now,
                actor
        );
        audit(
                id,
                "CREATE_VERSION",
                "{\"sourceVersion\":" + current.currentVersion()
                        + ",\"newVersion\":" + nextVersion + "}",
                now,
                actor
        );
        return getInternal(id);
    }

    public List<PromptData> search(PromptSearchCriteria criteria) {
        return repository.search(criteria).stream()
                .filter(permissionPort::canRead)
                .toList();
    }

    public List<PromptData> publishedCatalog() {
        return repository.search(new PromptSearchCriteria(
                null, null, null, null, null
        )).stream()
                .filter(prompt -> prompt.publishedVersion() != null)
                .filter(prompt -> prompt.status() != PromptStatus.DEPRECATED
                        && prompt.status() != PromptStatus.ARCHIVED)
                .filter(permissionPort::canRead)
                .toList();
    }

    public PromptView get(String id) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        return view(prompt);
    }

    public List<PromptVersionData> versions(String id) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        return repository.findVersions(id);
    }

    public List<AuditEntry> audit(String id) {
        PromptData prompt = requirePrompt(id);
        requireManage(prompt);
        return repository.findAudit(id);
    }

    public List<PromptRenderLog> renderLogs(String id, int limit) {
        PromptData prompt = requirePrompt(id);
        requireManage(prompt);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findRenderLogs(id, safeLimit);
    }

    @Transactional
    public PromptView transition(String id, PromptStatus target) {
        PromptData current = requirePrompt(id);
        requireManage(current);
        if (target == null) {
            throw unprocessable("AI_PROMPT_STATUS_REQUIRED", "Target Prompt status is required");
        }
        PromptStatus nextStatus = target;
        Integer publishedVersion = current.publishedVersion();
        PromptVersionData version = requireVersion(id, current.currentVersion());
        String actor = requestContext.actor();
        Instant now = Instant.now();

        switch (current.status()) {
            case DRAFT -> {
                if (target == PromptStatus.TESTING) {
                    validatePublishable(current, version);
                } else if (target != PromptStatus.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
            }
            case TESTING -> {
                if (target == PromptStatus.PUBLISHED) {
                    PromptVersionData tested = requireVersion(id, current.currentVersion());
                    validatePublishable(current, tested);
                    if (!tested.testsPassed()) {
                        throw conflict(
                                "AI_PROMPT_TEST_REQUIRED",
                                "Run and pass all enabled Prompt test cases before publishing"
                        );
                    }
                    repository.markVersionPublished(tested.id(), now, actor);
                    publishedVersion = tested.version();
                } else if (target != PromptStatus.DRAFT) {
                    invalidTransition(current.status(), target);
                }
            }
            case PUBLISHED -> {
                if (target != PromptStatus.DEPRECATED) {
                    invalidTransition(current.status(), target);
                }
                publishedVersion = null;
            }
            case DEPRECATED -> {
                if (target != PromptStatus.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
                publishedVersion = null;
            }
            case ARCHIVED -> invalidTransition(current.status(), target);
        }
        if (target == PromptStatus.ARCHIVED) {
            publishedVersion = null;
        }

        repository.updatePrompt(
                current,
                nextStatus,
                current.currentVersion(),
                publishedVersion,
                now,
                actor
        );
        audit(
                id,
                "STATUS_CHANGE",
                "{\"from\":\"" + current.status() + "\",\"to\":\"" + target
                        + "\",\"version\":" + current.currentVersion() + "}",
                now,
                actor
        );
        return getInternal(id);
    }

    @Transactional
    public PromptView rollback(String id, int version) {
        PromptData current = requirePrompt(id);
        requireManage(current);
        if (current.status() == PromptStatus.ARCHIVED) {
            throw conflict("AI_PROMPT_ARCHIVED", "Archived Prompt cannot be changed");
        }
        PromptVersionData source = requireVersion(id, version);
        int nextVersion = repository.maxVersion(id) + 1;
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String versionId = UUID.randomUUID().toString();
        PromptVersionData copy = copyVersion(
                source,
                versionId,
                nextVersion,
                "Rollback from V" + version,
                now,
                actor
        );
        repository.insertVersion(copy);
        repository.copyTestCases(source.id(), versionId, now, actor);
        repository.updatePrompt(
                current,
                PromptStatus.DRAFT,
                nextVersion,
                current.publishedVersion(),
                now,
                actor
        );
        audit(
                id,
                "ROLLBACK",
                "{\"sourceVersion\":" + version + ",\"newVersion\":" + nextVersion + "}",
                now,
                actor
        );
        return getInternal(id);
    }

    public PromptRenderResult renderCurrent(String id, Map<String, Object> variables) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        PromptVersionData version = requireVersion(id, prompt.currentVersion());
        return renderAndLog(prompt, version, variables, "PLAYGROUND");
    }

    public PromptRenderResult renderRuntime(
            String code,
            Integer version,
            Map<String, Object> variables
    ) {
        PromptData prompt = requirePromptByReference(code);
        requireRead(prompt);
        PromptVersionData published = requirePublishedVersion(prompt, version);
        return renderAndLog(prompt, published, variables, "RUNTIME");
    }

    @Override
    public PromptReference resolvePublished(String reference, Integer version) {
        PromptData prompt = requirePromptByReference(reference);
        requireRead(prompt);
        PromptVersionData published = requirePublishedVersion(prompt, version);
        return new PromptReference(prompt.id(), prompt.code(), published.version());
    }

    @Override
    public PromptRenderResult renderPublished(
            String reference,
            Integer version,
            Map<String, Object> variables
    ) {
        PromptData prompt = requirePromptByReference(reference);
        requireRead(prompt);
        PromptVersionData published = requirePublishedVersion(prompt, version);
        return renderAndLog(prompt, published, variables, "SCENE");
    }

    public void validateOutput(String id, Integer version, String output) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        PromptVersionData selected = version == null
                ? requireVersion(id, prompt.currentVersion())
                : requireVersion(id, version);
        validateOutput(selected, output);
    }

    public void validatePublishedOutput(String reference, Integer version, String output) {
        PromptData prompt = requirePromptByReference(reference);
        requireRead(prompt);
        validateOutput(requirePublishedVersion(prompt, version), output);
    }

    private void validateOutput(PromptVersionData selected, String output) {
        PromptOutputSchema schema = selected.outputSchema();
        if (!schema.configured()) {
            throw unprocessable(
                    "AI_PROMPT_SCHEMA_NOT_CONFIGURED",
                    "Prompt version has no output Schema"
            );
        }
        schemaValidator.validateOutput(schema.schemaJson(), output);
        guardrailEngine.validateOutput(selected.guardrails(), output, schema.schemaJson());
    }

    public List<PromptDiffLine> compare(String id, int leftVersion, int rightVersion) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        PromptVersionData left = requireVersion(id, leftVersion);
        PromptVersionData right = requireVersion(id, rightVersion);
        List<PromptDiffLine> result = new ArrayList<>();
        result.addAll(diffService.compare("SYSTEM", left.systemPrompt(), right.systemPrompt()));
        result.addAll(diffService.compare("USER", left.userPrompt(), right.userPrompt()));
        result.addAll(diffService.compare("ASSISTANT", left.assistantPrompt(), right.assistantPrompt()));
        return List.copyOf(result);
    }

    public List<PromptTestCase> testCases(String id) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        PromptVersionData current = requireVersion(id, prompt.currentVersion());
        return repository.findTestCases(current.id());
    }

    @Transactional
    public PromptTestCase createTestCase(
            String id,
            String name,
            String inputJson,
            String expectedOutput,
            boolean enabled
    ) {
        PromptData prompt = requirePrompt(id);
        requireManage(prompt);
        requireEditable(prompt);
        PromptVersionData current = requireVersion(id, prompt.currentVersion());
        String actor = requestContext.actor();
        Instant now = Instant.now();
        PromptTestCase testCase = new PromptTestCase(
                UUID.randomUUID().toString(),
                current.id(),
                trimRequired(name, 200, "AI_PROMPT_TEST_NAME_INVALID", "Test case name"),
                normalizeInputJson(inputJson),
                trimOptional(expectedOutput, 500_000),
                enabled,
                null,
                null,
                null,
                now,
                now,
                actor,
                actor
        );
        PromptTestCase saved = repository.insertTestCase(testCase);
        repository.markVersionTested(current.id(), false, now, actor);
        audit(id, "CREATE_TEST_CASE", "{\"testCaseId\":\"" + saved.id() + "\"}", now, actor);
        return saved;
    }

    @Transactional
    public PromptTestCase updateTestCase(
            String promptId,
            String testCaseId,
            String name,
            String inputJson,
            String expectedOutput,
            boolean enabled
    ) {
        PromptData prompt = requirePrompt(promptId);
        requireManage(prompt);
        requireEditable(prompt);
        PromptVersionData current = requireVersion(promptId, prompt.currentVersion());
        PromptTestCase existing = requireTestCase(testCaseId);
        requireCurrentTestCase(current, existing);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        PromptTestCase saved = repository.updateTestCase(new PromptTestCase(
                existing.id(),
                existing.promptVersionId(),
                trimRequired(name, 200, "AI_PROMPT_TEST_NAME_INVALID", "Test case name"),
                normalizeInputJson(inputJson),
                trimOptional(expectedOutput, 500_000),
                enabled,
                null,
                null,
                null,
                existing.createTime(),
                now,
                existing.createUser(),
                actor
        ));
        repository.markVersionTested(current.id(), false, now, actor);
        audit(promptId, "UPDATE_TEST_CASE", "{\"testCaseId\":\"" + saved.id() + "\"}", now, actor);
        return saved;
    }

    @Transactional
    public void deleteTestCase(String promptId, String testCaseId) {
        PromptData prompt = requirePrompt(promptId);
        requireManage(prompt);
        requireEditable(prompt);
        PromptVersionData current = requireVersion(promptId, prompt.currentVersion());
        PromptTestCase existing = requireTestCase(testCaseId);
        requireCurrentTestCase(current, existing);
        repository.deleteTestCase(testCaseId);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.markVersionTested(current.id(), false, now, actor);
        audit(promptId, "DELETE_TEST_CASE", "{\"testCaseId\":\"" + testCaseId + "\"}", now, actor);
    }

    @Transactional
    public PromptTestSuiteResult runTests(String id) {
        PromptData prompt = requirePrompt(id);
        requireManage(prompt);
        if (prompt.status() != PromptStatus.TESTING) {
            throw conflict("AI_PROMPT_TEST_STATUS_INVALID", "Prompt must be in Testing status");
        }
        PromptVersionData version = requireVersion(id, prompt.currentVersion());
        List<PromptTestCase> enabled = repository.findTestCases(version.id()).stream()
                .filter(PromptTestCase::enabled)
                .toList();
        if (enabled.isEmpty()) {
            throw conflict(
                    "AI_PROMPT_TEST_CASE_REQUIRED",
                    "At least one enabled Prompt test case is required"
            );
        }
        List<PromptEvaluationResult> results = new ArrayList<>();
        String actor = requestContext.actor();
        Instant now = Instant.now();
        for (PromptTestCase testCase : enabled) {
            PromptRenderResult render = renderVersion(
                    prompt,
                    version,
                    readInput(testCase.inputJson()),
                    new LinkedHashSet<>(),
                    0
            );
            PromptEvaluationResult result = evaluationPort.evaluate(
                    new PromptEvaluationPort.EvaluationRequest(
                            testCase,
                            render,
                            requestContext.traceId()
                    )
            );
            repository.updateTestResult(
                    testCase.id(),
                    result.actualOutput(),
                    result.passed(),
                    now,
                    actor
            );
            results.add(result);
        }
        boolean passed = results.stream().allMatch(PromptEvaluationResult::passed);
        repository.markVersionTested(version.id(), passed, now, actor);
        audit(
                id,
                "RUN_TESTS",
                "{\"version\":" + version.version() + ",\"passed\":" + passed
                        + ",\"count\":" + results.size() + "}",
                now,
                actor
        );
        return new PromptTestSuiteResult(
                id,
                version.version(),
                passed,
                results.getFirst().mode(),
                results.getFirst().executed(),
                results
        );
    }

    public List<PromptAbTest> abTests(String id) {
        PromptData prompt = requirePrompt(id);
        requireRead(prompt);
        return repository.findAbTests(id);
    }

    @Transactional
    public PromptAbTest createAbTest(
            String promptId,
            String name,
            String sceneId,
            int versionA,
            int versionB,
            int trafficRatio
    ) {
        PromptData prompt = requirePrompt(promptId);
        requireManage(prompt);
        if (versionA == versionB) {
            throw unprocessable("AI_PROMPT_AB_VERSION_INVALID", "A/B versions must be different");
        }
        requirePublishedArtifact(promptId, versionA);
        requirePublishedArtifact(promptId, versionB);
        if (trafficRatio < 1 || trafficRatio > 99) {
            throw unprocessable("AI_PROMPT_AB_RATIO_INVALID", "Traffic ratio must be 1-99");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        PromptAbTest saved = repository.insertAbTest(new PromptAbTest(
                UUID.randomUUID().toString(),
                promptId,
                trimOptional(sceneId, 64),
                trimRequired(name, 200, "AI_PROMPT_AB_NAME_INVALID", "A/B test name"),
                versionA,
                versionB,
                trafficRatio,
                true,
                0,
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                now,
                now,
                actor,
                actor
        ));
        audit(promptId, "CREATE_AB_TEST", "{\"abTestId\":\"" + saved.id() + "\"}", now, actor);
        return saved;
    }

    public PromptAbAssignment assignAbTest(String promptId, String abTestId, String subjectKey) {
        PromptData prompt = requirePrompt(promptId);
        requireRead(prompt);
        PromptAbTest test = requireAbTest(abTestId);
        requireAbPrompt(promptId, test);
        if (!test.enabled()) {
            throw conflict("AI_PROMPT_AB_DISABLED", "A/B test is disabled");
        }
        String key = trimRequired(
                subjectKey,
                500,
                "AI_PROMPT_AB_SUBJECT_INVALID",
                "A/B subject key"
        );
        int bucket = stableBucket(test.id() + ":" + key);
        boolean a = bucket < test.trafficRatio();
        return new PromptAbAssignment(
                test.id(),
                a ? "A" : "B",
                a ? test.versionA() : test.versionB(),
                bucket
        );
    }

    @Transactional
    public PromptAbTest recordAbObservation(
            String promptId,
            String abTestId,
            String variant,
            boolean success,
            long latencyMs,
            BigDecimal cost,
            double score
    ) {
        PromptData prompt = requirePrompt(promptId);
        requireManage(prompt);
        PromptAbTest test = requireAbTest(abTestId);
        requireAbPrompt(promptId, test);
        String normalizedVariant = variant == null ? "" : variant.trim().toUpperCase(Locale.ROOT);
        if (!normalizedVariant.equals("A") && !normalizedVariant.equals("B")) {
            throw unprocessable("AI_PROMPT_AB_VARIANT_INVALID", "A/B variant must be A or B");
        }
        if (latencyMs < 0 || score < 0 || score > 5) {
            throw unprocessable(
                    "AI_PROMPT_AB_OBSERVATION_INVALID",
                    "Latency must be positive and score must be 0-5"
            );
        }
        BigDecimal safeCost = cost == null ? BigDecimal.ZERO : cost;
        if (safeCost.signum() < 0) {
            throw unprocessable("AI_PROMPT_AB_OBSERVATION_INVALID", "Cost cannot be negative");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.recordAbObservation(
                abTestId,
                normalizedVariant,
                success,
                latencyMs,
                safeCost,
                score,
                now,
                actor
        );
        audit(
                promptId,
                "RECORD_AB_OBSERVATION",
                "{\"abTestId\":\"" + abTestId + "\",\"variant\":\""
                        + normalizedVariant + "\"}",
                now,
                actor
        );
        return requireAbTest(abTestId);
    }

    private PromptRenderResult renderAndLog(
            PromptData prompt,
            PromptVersionData version,
            Map<String, Object> variables,
            String mode
    ) {
        PromptRenderResult render = renderVersion(
                prompt,
                version,
                variables,
                new LinkedHashSet<>(),
                0
        );
        writeRenderLog(prompt, version, variables, render, mode);
        return new PromptRenderResult(
                render.promptId(),
                render.promptCode(),
                render.version(),
                render.systemPrompt(),
                render.userPrompt(),
                render.assistantPrompt(),
                render.chain(),
                render.characterCount(),
                render.estimatedTokens(),
                render.outputSchema(),
                render.strictSchema(),
                mode
        );
    }

    private PromptRenderResult renderVersion(
            PromptData prompt,
            PromptVersionData version,
            Map<String, Object> variables,
            LinkedHashSet<String> visited,
            int depth
    ) {
        if (depth > properties.maxChainDepth()) {
            throw conflict("AI_PROMPT_CHAIN_DEPTH_EXCEEDED", "Prompt chain depth exceeds limit");
        }
        String visitKey = prompt.id() + ":" + version.version();
        if (!visited.add(visitKey)) {
            throw conflict("AI_PROMPT_CHAIN_CYCLE", "Prompt chain contains a cycle");
        }
        try {
            guardrailEngine.validateInput(version.guardrails(), variables);
            PromptTemplateRenderer.RenderedTemplates rendered = renderer.render(
                    version.systemPrompt(),
                    version.userPrompt(),
                    version.assistantPrompt(),
                    version.variables(),
                    variables
            );
            List<PromptRenderedStage> chain = new ArrayList<>();
            for (PromptChainStep step : version.chain()) {
                try {
                    PromptData target = requirePromptByReference(step.reference());
                    PromptVersionData targetVersion = requirePublishedVersion(target, step.version());
                    PromptRenderResult stage = renderVersion(
                            target,
                            targetVersion,
                            variables,
                            visited,
                            depth + 1
                    );
                    chain.add(new PromptRenderedStage(
                            stage.promptId(),
                            stage.promptCode(),
                            stage.version(),
                            stage.systemPrompt(),
                            stage.userPrompt(),
                            stage.assistantPrompt(),
                            stage.estimatedTokens()
                    ));
                    chain.addAll(stage.chain());
                } catch (ProviderOperationException exception) {
                    if (!step.optional() || !isOptionalDependencyFailure(exception)) {
                        throw exception;
                    }
                }
            }
            String combined = combine(
                    rendered.systemPrompt(),
                    rendered.userPrompt(),
                    rendered.assistantPrompt()
            );
            int characterCount = combined.length();
            int estimatedTokens = estimateTokens(characterCount)
                    + chain.stream().mapToInt(PromptRenderedStage::estimatedTokens).sum();
            PromptOutputSchema schema = version.outputSchema();
            return new PromptRenderResult(
                    prompt.id(),
                    prompt.code(),
                    version.version(),
                    rendered.systemPrompt(),
                    rendered.userPrompt(),
                    rendered.assistantPrompt(),
                    chain,
                    characterCount,
                    estimatedTokens,
                    schema.schemaJson(),
                    schema.strictMode(),
                    "PREVIEW"
            );
        } finally {
            visited.remove(visitKey);
        }
    }

    private void validatePublishable(PromptData prompt, PromptVersionData version) {
        validateChain(prompt, version, new LinkedHashSet<>(), 0);
        schemaValidator.validateSchema(version.outputSchema().schemaJson());
    }

    private void validateChain(
            PromptData prompt,
            PromptVersionData version,
            Set<String> visited,
            int depth
    ) {
        if (depth > properties.maxChainDepth()) {
            throw conflict("AI_PROMPT_CHAIN_DEPTH_EXCEEDED", "Prompt chain depth exceeds limit");
        }
        String key = prompt.id() + ":" + version.version();
        if (!visited.add(key)) {
            throw conflict("AI_PROMPT_CHAIN_CYCLE", "Prompt chain contains a cycle");
        }
        for (PromptChainStep step : version.chain()) {
            try {
                PromptData target = requirePromptByReference(step.reference());
                PromptVersionData targetVersion = requirePublishedVersion(target, step.version());
                validateChain(target, targetVersion, visited, depth + 1);
            } catch (ProviderOperationException exception) {
                if (!step.optional() || !isOptionalDependencyFailure(exception)) {
                    throw exception;
                }
            }
        }
        visited.remove(key);
    }

    private NormalizedPrompt normalize(
            String promptId,
            String promptCode,
            String versionId,
            PromptConfiguration configuration,
            Instant now,
            String actor
    ) {
        if (configuration == null) {
            throw unprocessable("AI_PROMPT_CONFIGURATION_REQUIRED", "Prompt configuration is required");
        }
        String name = trimRequired(
                configuration.name(),
                200,
                "AI_PROMPT_NAME_INVALID",
                "Prompt name"
        );
        String category = normalizeCategory(configuration.category());
        String description = trimOptional(configuration.description(), 4000);
        String sceneId = trimOptional(configuration.sceneId(), 64);
        PromptVisibility visibility = configuration.visibility() == null
                ? PromptVisibility.PUBLIC
                : configuration.visibility();
        String projectCode = trimOptional(configuration.projectCode(), 100);
        String departmentCode = trimOptional(configuration.departmentCode(), 100);
        if (visibility == PromptVisibility.PROJECT && projectCode == null) {
            throw unprocessable("AI_PROMPT_PROJECT_REQUIRED", "Project visibility requires projectCode");
        }
        if (visibility == PromptVisibility.DEPARTMENT && departmentCode == null) {
            throw unprocessable(
                    "AI_PROMPT_DEPARTMENT_REQUIRED",
                    "Department visibility requires departmentCode"
            );
        }
        String systemPrompt = trimTemplate(configuration.systemPrompt(), false, "System Prompt");
        String userPrompt = trimTemplate(configuration.userPrompt(), true, "User Prompt");
        String assistantPrompt = trimTemplate(
                configuration.assistantPrompt(),
                false,
                "Assistant Prompt"
        );
        List<PromptVariable> variables = normalizeVariables(
                versionId,
                configuration.variables(),
                now,
                actor
        );
        Set<String> declared = variables.stream()
                .map(PromptVariable::name)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> referenced = renderer.referencedVariables(
                systemPrompt,
                userPrompt,
                assistantPrompt
        );
        if (!declared.containsAll(referenced)) {
            Set<String> missing = new HashSet<>(referenced);
            missing.removeAll(declared);
            throw unprocessable(
                    "AI_PROMPT_VARIABLE_UNDECLARED",
                    "Template uses undeclared variables: " + String.join(", ", missing)
            );
        }
        validateDefaults(variables);
        PromptOutputSchema schema = normalizeSchema(
                versionId,
                configuration.outputSchema(),
                now,
                actor
        );
        List<PromptGuardrail> guardrails = normalizeGuardrails(
                versionId,
                configuration.guardrails(),
                now,
                actor
        );
        if (!schema.configured() && guardrails.stream().anyMatch(item ->
                item.enabled() && item.type() == PromptGuardrailType.JSON_VALIDATE
        )) {
            throw unprocessable(
                    "AI_PROMPT_GUARDRAIL_SCHEMA_REQUIRED",
                    "JSON_VALIDATE guardrail requires an output Schema"
            );
        }
        List<PromptChainStep> chain = normalizeChain(
                promptId,
                promptCode,
                configuration.chain()
        );
        return new NormalizedPrompt(
                name,
                description,
                category,
                sceneId,
                visibility,
                visibility == PromptVisibility.PROJECT ? projectCode : null,
                visibility == PromptVisibility.DEPARTMENT ? departmentCode : null,
                systemPrompt,
                userPrompt,
                assistantPrompt,
                trimOptional(configuration.changeLog(), 1000),
                variables,
                schema,
                guardrails,
                chain
        );
    }

    private List<PromptVariable> normalizeVariables(
            String versionId,
            List<PromptVariable> variables,
            Instant now,
            String actor
    ) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        if (variables.size() > 100) {
            throw unprocessable("AI_PROMPT_VARIABLE_LIMIT", "Prompt supports at most 100 variables");
        }
        Set<String> names = new HashSet<>();
        List<PromptVariable> result = new ArrayList<>();
        for (PromptVariable variable : variables) {
            String name = variable.name() == null ? "" : variable.name().trim();
            if (!VARIABLE_PATTERN.matcher(name).matches()) {
                throw unprocessable("AI_PROMPT_VARIABLE_NAME_INVALID", "Prompt variable name is invalid");
            }
            if (!names.add(name)) {
                throw conflict("AI_PROMPT_VARIABLE_DUPLICATE", "Prompt variables must be unique");
            }
            if (variable.type() == null) {
                throw unprocessable("AI_PROMPT_VARIABLE_TYPE_REQUIRED", "Prompt variable type is required");
            }
            result.add(new PromptVariable(
                    UUID.randomUUID().toString(),
                    versionId,
                    name,
                    variable.type(),
                    variable.required(),
                    trimOptional(variable.defaultValue(), 100_000),
                    trimOptional(variable.description(), 1000),
                    now,
                    now,
                    actor,
                    actor
            ));
        }
        return result.stream().sorted(Comparator.comparing(PromptVariable::name)).toList();
    }

    private void validateDefaults(List<PromptVariable> variables) {
        for (PromptVariable variable : variables) {
            if (variable.defaultValue() == null) {
                continue;
            }
            renderer.render(
                    null,
                    "{{" + variable.name() + "}}",
                    null,
                    List.of(variable),
                    Map.of()
            );
        }
    }

    private PromptOutputSchema normalizeSchema(
            String versionId,
            PromptOutputSchema schema,
            Instant now,
            String actor
    ) {
        if (schema == null || !schema.configured()) {
            return PromptOutputSchema.empty();
        }
        if (schema.schemaJson().length() > 200_000) {
            throw unprocessable("AI_PROMPT_SCHEMA_TOO_LARGE", "Output Schema is too large");
        }
        schemaValidator.validateSchema(schema.schemaJson());
        return new PromptOutputSchema(
                UUID.randomUUID().toString(),
                versionId,
                compactJson(schema.schemaJson(), "AI_PROMPT_SCHEMA_INVALID"),
                schema.strictMode(),
                now,
                now,
                actor,
                actor
        );
    }

    private List<PromptGuardrail> normalizeGuardrails(
            String versionId,
            List<PromptGuardrail> guardrails,
            Instant now,
            String actor
    ) {
        if (guardrails == null || guardrails.isEmpty()) {
            return List.of();
        }
        if (guardrails.size() > 50) {
            throw unprocessable("AI_PROMPT_GUARDRAIL_LIMIT", "Prompt supports at most 50 guardrails");
        }
        List<PromptGuardrail> result = new ArrayList<>();
        for (PromptGuardrail guardrail : guardrails) {
            if (guardrail.type() == null || guardrail.phase() == null) {
                throw unprocessable(
                        "AI_PROMPT_GUARDRAIL_REQUIRED",
                        "Guardrail type and phase are required"
                );
            }
            PromptGuardrail normalized = new PromptGuardrail(
                    UUID.randomUUID().toString(),
                    versionId,
                    guardrail.type(),
                    guardrail.phase(),
                    compactJson(
                            guardrail.configJson() == null ? "{}" : guardrail.configJson(),
                            "AI_PROMPT_GUARDRAIL_CONFIG_INVALID"
                    ),
                    guardrail.enabled(),
                    now,
                    now,
                    actor,
                    actor
            );
            guardrailEngine.validateConfiguration(normalized);
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private List<PromptChainStep> normalizeChain(
            String promptId,
            String promptCode,
            List<PromptChainStep> chain
    ) {
        if (chain == null || chain.isEmpty()) {
            return List.of();
        }
        if (chain.size() > 20) {
            throw unprocessable("AI_PROMPT_CHAIN_LIMIT", "Prompt chain supports at most 20 steps");
        }
        List<PromptChainStep> result = new ArrayList<>();
        for (PromptChainStep step : chain) {
            String reference = trimRequired(
                    step.reference(),
                    100,
                    "AI_PROMPT_CHAIN_REFERENCE_INVALID",
                    "Prompt chain reference"
            );
            if (reference.equals(promptId) || reference.equalsIgnoreCase(promptCode)) {
                throw conflict("AI_PROMPT_CHAIN_SELF_REFERENCE", "Prompt cannot reference itself");
            }
            if (step.version() != null && step.version() < 1) {
                throw unprocessable(
                        "AI_PROMPT_CHAIN_VERSION_INVALID",
                        "Prompt chain version must be positive"
                );
            }
            result.add(new PromptChainStep(reference, step.version(), step.optional()));
        }
        return List.copyOf(result);
    }

    private PromptVersionData version(
            String promptId,
            String versionId,
            int number,
            NormalizedPrompt normalized,
            Instant now,
            String actor
    ) {
        return new PromptVersionData(
                versionId,
                promptId,
                number,
                normalized.systemPrompt(),
                normalized.userPrompt(),
                normalized.assistantPrompt(),
                normalized.changeLog(),
                normalized.variables(),
                normalized.outputSchema(),
                normalized.guardrails(),
                normalized.chain(),
                false,
                null,
                null,
                now,
                now,
                actor,
                actor
        );
    }

    private PromptVersionData copyVersion(
            PromptVersionData source,
            String versionId,
            int number,
            String changeLog,
            Instant now,
            String actor
    ) {
        List<PromptVariable> variables = source.variables().stream()
                .map(variable -> new PromptVariable(
                        UUID.randomUUID().toString(),
                        versionId,
                        variable.name(),
                        variable.type(),
                        variable.required(),
                        variable.defaultValue(),
                        variable.description(),
                        now,
                        now,
                        actor,
                        actor
                ))
                .toList();
        PromptOutputSchema schema = source.outputSchema().configured()
                ? new PromptOutputSchema(
                UUID.randomUUID().toString(),
                versionId,
                source.outputSchema().schemaJson(),
                source.outputSchema().strictMode(),
                now,
                now,
                actor,
                actor
        )
                : PromptOutputSchema.empty();
        List<PromptGuardrail> guardrails = source.guardrails().stream()
                .map(item -> new PromptGuardrail(
                        UUID.randomUUID().toString(),
                        versionId,
                        item.type(),
                        item.phase(),
                        item.configJson(),
                        item.enabled(),
                        now,
                        now,
                        actor,
                        actor
                ))
                .toList();
        return new PromptVersionData(
                versionId,
                source.promptId(),
                number,
                source.systemPrompt(),
                source.userPrompt(),
                source.assistantPrompt(),
                changeLog,
                variables,
                schema,
                guardrails,
                source.chain(),
                false,
                null,
                null,
                now,
                now,
                actor,
                actor
        );
    }

    private PromptData metadata(
            PromptData current,
            NormalizedPrompt normalized,
            PromptStatus status,
            int version,
            Instant now,
            String actor
    ) {
        return new PromptData(
                current.id(),
                current.code(),
                normalized.name(),
                normalized.description(),
                normalized.category(),
                normalized.sceneId(),
                status,
                version,
                current.publishedVersion(),
                normalized.visibility(),
                normalized.projectCode(),
                normalized.departmentCode(),
                current.ownerUser(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
    }

    private PromptView getInternal(String id) {
        return view(requirePrompt(id));
    }

    private PromptView view(PromptData prompt) {
        PromptVersionData current = requireVersion(prompt.id(), prompt.currentVersion());
        PromptVersionData published = prompt.publishedVersion() == null
                ? null
                : requireVersion(prompt.id(), prompt.publishedVersion());
        return new PromptView(prompt, current, published);
    }

    private PromptData requirePrompt(String id) {
        return repository.findById(id)
                .orElseThrow(() -> notFound("AI_PROMPT_NOT_FOUND", "Prompt not found"));
    }

    private PromptData requirePromptByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw notFound("AI_PROMPT_NOT_FOUND", "Prompt not found");
        }
        String normalized = reference.trim();
        return repository.findById(normalized)
                .or(() -> repository.findByCode(normalized.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> notFound("AI_PROMPT_NOT_FOUND", "Prompt not found"));
    }

    private PromptVersionData requireVersion(String promptId, int version) {
        return repository.findVersion(promptId, version)
                .orElseThrow(() -> notFound("AI_PROMPT_VERSION_NOT_FOUND", "Prompt version not found"));
    }

    private PromptVersionData requirePublishedVersion(PromptData prompt, Integer requestedVersion) {
        if (prompt.publishedVersion() == null
                || prompt.status() == PromptStatus.DEPRECATED
                || prompt.status() == PromptStatus.ARCHIVED) {
            throw notFound("AI_PROMPT_NOT_PUBLISHED", "Published Prompt not found");
        }
        int version = requestedVersion == null ? prompt.publishedVersion() : requestedVersion;
        PromptVersionData selected = requireVersion(prompt.id(), version);
        if (selected.publishedTime() == null) {
            throw notFound("AI_PROMPT_VERSION_NOT_PUBLISHED", "Published Prompt version not found");
        }
        return selected;
    }

    private PromptVersionData requirePublishedArtifact(String promptId, int version) {
        PromptVersionData selected = requireVersion(promptId, version);
        if (selected.publishedTime() == null) {
            throw conflict(
                    "AI_PROMPT_AB_VERSION_NOT_PUBLISHED",
                    "A/B versions must have been published"
            );
        }
        return selected;
    }

    private PromptTestCase requireTestCase(String id) {
        return repository.findTestCase(id)
                .orElseThrow(() -> notFound("AI_PROMPT_TEST_CASE_NOT_FOUND", "Test case not found"));
    }

    private PromptAbTest requireAbTest(String id) {
        return repository.findAbTest(id)
                .orElseThrow(() -> notFound("AI_PROMPT_AB_NOT_FOUND", "A/B test not found"));
    }

    private void requireAbPrompt(String promptId, PromptAbTest test) {
        if (!test.promptId().equals(promptId)) {
            throw notFound("AI_PROMPT_AB_NOT_FOUND", "A/B test not found");
        }
    }

    private void requireCurrentTestCase(PromptVersionData current, PromptTestCase testCase) {
        if (!current.id().equals(testCase.promptVersionId())) {
            throw conflict(
                    "AI_PROMPT_TEST_CASE_VERSION_INVALID",
                    "Only current Prompt version test cases can be changed"
            );
        }
    }

    private void requireEditable(PromptData prompt) {
        if (prompt.status() != PromptStatus.DRAFT && prompt.status() != PromptStatus.TESTING) {
            throw conflict(
                    "AI_PROMPT_EDIT_STATUS_INVALID",
                    "Prompt test cases can only change in Draft or Testing"
            );
        }
    }

    private void requireRead(PromptData prompt) {
        if (!permissionPort.canRead(prompt)) {
            throw forbidden("AI_PROMPT_ACCESS_DENIED", "Prompt access denied");
        }
    }

    private void requireManage(PromptData prompt) {
        if (!permissionPort.canManage(prompt)) {
            throw forbidden("AI_PROMPT_MANAGE_DENIED", "Prompt manage access denied");
        }
    }

    private void writeRenderLog(
            PromptData prompt,
            PromptVersionData version,
            Map<String, Object> variables,
            PromptRenderResult render,
            String mode
    ) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        String variableNames = writeJson(safeVariables.keySet().stream().sorted().toList());
        String combined = render.combinedPrompt();
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.addRenderLog(new PromptRenderLog(
                UUID.randomUUID().toString(),
                prompt.id(),
                version.id(),
                variableNames,
                properties.renderLogContentEnabled() ? writeJson(safeVariables) : null,
                properties.renderLogContentEnabled() ? combined : null,
                sha256(writeJson(safeVariables) + "\n" + combined),
                render.estimatedTokens(),
                mode,
                now.plus(7, ChronoUnit.DAYS),
                now,
                actor
        ), now, actor);
        repository.pruneExpiredRenderLogs(now);
        repository.pruneRenderLogs(prompt.id(), properties.renderLogMaxEntries());
    }

    private Map<String, Object> readInput(String inputJson) {
        try {
            return objectMapper.readValue(
                    inputJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw unprocessable("AI_PROMPT_TEST_INPUT_INVALID", "Test case input must be a JSON object");
        }
    }

    private String normalizeInputJson(String inputJson) {
        String source = inputJson == null || inputJson.isBlank() ? "{}" : inputJson;
        try {
            JsonNode node = objectMapper.readTree(source);
            if (!node.isObject()) {
                throw unprocessable(
                        "AI_PROMPT_TEST_INPUT_INVALID",
                        "Test case input must be a JSON object"
                );
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw unprocessable("AI_PROMPT_TEST_INPUT_INVALID", "Test case input is invalid JSON");
        }
    }

    private String compactJson(String json, String errorCode) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw unprocessable(errorCode, "JSON configuration is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Prompt value", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int stableBucket(String value) {
        String hash = sha256(value);
        return (int) (Long.parseLong(hash.substring(0, 8), 16) % 100);
    }

    private int estimateTokens(int characters) {
        return Math.max(1, (characters + 3) / 4);
    }

    private String combine(String system, String user, String assistant) {
        List<String> sections = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            sections.add(system);
        }
        if (user != null && !user.isBlank()) {
            sections.add(user);
        }
        if (assistant != null && !assistant.isBlank()) {
            sections.add(assistant);
        }
        return String.join("\n\n", sections);
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw unprocessable(
                    "AI_PROMPT_CODE_INVALID",
                    "Prompt code must contain 2-100 lowercase letters, numbers, dots, underscores or hyphens"
            );
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORY_PATTERN.matcher(normalized).matches()) {
            throw unprocessable(
                    "AI_PROMPT_CATEGORY_INVALID",
                    "Prompt category must contain 2-100 letters, numbers, dots, underscores or hyphens"
            );
        }
        return normalized;
    }

    private String trimTemplate(String value, boolean required, String field) {
        if ((value == null || value.isBlank()) && required) {
            throw unprocessable("AI_PROMPT_TEMPLATE_REQUIRED", field + " is required");
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_TEMPLATE_CHARS) {
            throw unprocessable(
                    "AI_PROMPT_TEMPLATE_TOO_LARGE",
                    field + " exceeds " + MAX_TEMPLATE_CHARS + " chars"
            );
        }
        return value;
    }

    private String trimRequired(String value, int max, String code, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw unprocessable(code, field + " is required and max " + max + " chars");
        }
        return value.trim();
    }

    private String trimOptional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw unprocessable("AI_PROMPT_TEXT_TOO_LONG", "Prompt text exceeds " + max + " chars");
        }
        return normalized;
    }

    private void invalidTransition(PromptStatus current, PromptStatus target) {
        throw conflict(
                "AI_PROMPT_STATUS_TRANSITION_INVALID",
                "Cannot transition Prompt from " + current + " to " + target
        );
    }

    private boolean isOptionalDependencyFailure(ProviderOperationException exception) {
        return Set.of(
                "AI_PROMPT_NOT_FOUND",
                "AI_PROMPT_NOT_PUBLISHED",
                "AI_PROMPT_VERSION_NOT_FOUND",
                "AI_PROMPT_VERSION_NOT_PUBLISHED"
        ).contains(exception.errorCode());
    }

    private void audit(String id, String action, String detail, Instant now, String actor) {
        repository.addAudit(new AuditEntry(
                UUID.randomUUID().toString(),
                "AI_PROMPT",
                id,
                action,
                "SUCCESS",
                detail,
                requestContext.traceId(),
                now,
                actor
        ));
    }

    private ProviderOperationException notFound(String code, String message) {
        return new ProviderOperationException(code, message, 404);
    }

    private ProviderOperationException forbidden(String code, String message) {
        return new ProviderOperationException(code, message, 403);
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException unprocessable(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    private record NormalizedPrompt(
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
    }
}
