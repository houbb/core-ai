package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;
import io.coreplatform.ai.application.domain.ToolModels.Execution;
import io.coreplatform.ai.application.domain.ToolModels.ExecutorRequest;
import io.coreplatform.ai.application.domain.ToolModels.ExecutorResult;
import io.coreplatform.ai.application.domain.ToolModels.MarketItem;
import io.coreplatform.ai.application.domain.ToolModels.Parameter;
import io.coreplatform.ai.application.domain.ToolModels.Policy;
import io.coreplatform.ai.application.domain.ToolModels.Status;
import io.coreplatform.ai.application.domain.ToolModels.TestCase;
import io.coreplatform.ai.application.domain.ToolModels.TestSuite;
import io.coreplatform.ai.application.domain.ToolModels.Tool;
import io.coreplatform.ai.application.domain.ToolModels.Version;
import io.coreplatform.ai.application.domain.ToolModels.View;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.AnalyticsEventPort;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.application.port.ToolExecutionPort;
import io.coreplatform.ai.application.port.ToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ToolService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");
    private static final Set<String> SECRET_KEYS = Set.of(
            "secret", "token", "password", "credential", "apikey", "api_key",
            "authorization", "身份证", "idcard"
    );

    private final ToolRepository repository;
    private final ToolExecutionPort executionPort;
    private final AnalyticsEventPort analytics;
    private final RequestContextPort requestContext;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    public ToolService(
            ToolRepository repository,
            ToolExecutionPort executionPort,
            AnalyticsEventPort analytics,
            RequestContextPort requestContext,
            JsonSchemaValidator schemaValidator,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.executionPort = executionPort;
        this.analytics = analytics;
        this.requestContext = requestContext;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
    }

    public List<Tool> search(String query) {
        return repository.search(query);
    }

    public View get(String id) {
        Tool tool = requireTool(id);
        return view(tool);
    }

    public List<Version> versions(String id) {
        requireTool(id);
        return repository.findVersions(id);
    }

    public List<TestCase> testCases(String id) {
        Tool tool = requireTool(id);
        return repository.findTestCases(requireVersion(tool, tool.currentVersion()).id());
    }

    public List<Execution> executions(String id, int limit) {
        requireTool(id);
        return repository.findExecutions(id, limit);
    }

    public List<MarketItem> market() {
        return repository.findMarket();
    }

    @Transactional
    public View create(String code, Configuration configuration) {
        String normalizedCode = normalizeCode(code);
        if (repository.existsByCode(normalizedCode)) {
            throw conflict("AI_TOOL_CODE_EXISTS", "Tool code already exists");
        }
        validateConfiguration(configuration);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String toolId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        Tool tool = new Tool(
                toolId,
                normalizedCode,
                required(configuration.name(), "Tool name", 200),
                trim(configuration.description(), 4000),
                required(configuration.category(), "Tool category", 100).toUpperCase(Locale.ROOT),
                required(configuration.toolType(), "Tool type", 40).toUpperCase(Locale.ROOT),
                trim(configuration.icon(), 40),
                actor,
                Status.DRAFT,
                1,
                null,
                now,
                now,
                actor,
                actor
        );
        repository.insertTool(tool);
        repository.insertVersion(version(toolId, versionId, 1, configuration, now, actor));
        repository.savePolicy(policy(toolId, configuration.policy(), now, actor));
        return view(tool);
    }

    @Transactional
    public View update(String id, Configuration configuration) {
        Tool current = requireTool(id);
        if (current.status() == Status.ARCHIVED) {
            throw conflict("AI_TOOL_ARCHIVED", "Archived Tool cannot be edited");
        }
        validateConfiguration(configuration);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        int nextVersion = repository.findVersions(id).stream()
                .mapToInt(Version::version).max().orElse(current.currentVersion()) + 1;
        String versionId = UUID.randomUUID().toString();
        repository.insertVersion(version(id, versionId, nextVersion, configuration, now, actor));
        Policy existingPolicy = repository.findPolicy(id);
        Policy updatedPolicy = policy(id, configuration.policy(), now, actor);
        repository.savePolicy(new Policy(
                existingPolicy.id(),
                updatedPolicy.toolId(),
                updatedPolicy.accessLevel(),
                updatedPolicy.readonly(),
                updatedPolicy.manualConfirm(),
                updatedPolicy.approvalRequired(),
                updatedPolicy.timeoutSeconds(),
                updatedPolicy.retryCount(),
                updatedPolicy.logContent(),
                updatedPolicy.retentionDays(),
                existingPolicy.createTime(),
                now,
                existingPolicy.createUser(),
                actor
        ));
        Tool updated = new Tool(
                current.id(),
                current.code(),
                required(configuration.name(), "Tool name", 200),
                trim(configuration.description(), 4000),
                required(configuration.category(), "Tool category", 100).toUpperCase(Locale.ROOT),
                required(configuration.toolType(), "Tool type", 40).toUpperCase(Locale.ROOT),
                trim(configuration.icon(), 40),
                current.ownerUser(),
                Status.DRAFT,
                nextVersion,
                current.publishedVersion(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        repository.updateTool(updated);
        return view(updated);
    }

    @Transactional
    public View transition(String id, Status target) {
        Tool current = requireTool(id);
        if (target == null) {
            throw invalid("AI_TOOL_STATUS_REQUIRED", "Target Tool status is required");
        }
        Integer published = current.publishedVersion();
        Instant now = Instant.now();
        String actor = requestContext.actor();
        switch (current.status()) {
            case DRAFT -> {
                if (target != Status.TESTING && target != Status.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
            }
            case TESTING -> {
                if (target == Status.PUBLISHED) {
                    Version version = requireVersion(current, current.currentVersion());
                    if (!version.testsPassed()) {
                        throw conflict("AI_TOOL_TEST_REQUIRED", "Run and pass Tool tests before publishing");
                    }
                    repository.markVersionPublished(version.id(), now, actor);
                    published = version.version();
                } else if (target != Status.DRAFT && target != Status.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
            }
            case PUBLISHED -> {
                if (target != Status.DEPRECATED && target != Status.DISABLED) {
                    invalidTransition(current.status(), target);
                }
                if (target == Status.DEPRECATED) {
                    published = null;
                }
            }
            case DISABLED -> {
                if (target != Status.PUBLISHED && target != Status.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
                if (target == Status.PUBLISHED && published == null) {
                    throw conflict("AI_TOOL_NO_PUBLISHED_VERSION", "Tool has no Published version");
                }
            }
            case DEPRECATED -> {
                if (target != Status.ARCHIVED) {
                    invalidTransition(current.status(), target);
                }
                published = null;
            }
            case ARCHIVED -> invalidTransition(current.status(), target);
        }
        if (target == Status.ARCHIVED) {
            published = null;
        }
        Tool updated = copy(current, target, current.currentVersion(), published, now, actor);
        repository.updateTool(updated);
        return view(updated);
    }

    @Transactional
    public TestCase createTestCase(
            String toolId,
            String name,
            String inputJson,
            String expectedResult,
            boolean enabled
    ) {
        Tool tool = requireTool(toolId);
        if (tool.status() == Status.ARCHIVED) {
            throw conflict("AI_TOOL_ARCHIVED", "Archived Tool cannot be tested");
        }
        Version version = requireVersion(tool, tool.currentVersion());
        Map<String, Object> input = parseMap(inputJson);
        validateInput(version, input);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.markVersionTested(version.id(), false, now, actor);
        return repository.insertTestCase(new TestCase(
                UUID.randomUUID().toString(),
                version.id(),
                required(name, "Test name", 200),
                json(input),
                trim(expectedResult, 500_000),
                enabled,
                null,
                null,
                null,
                now,
                now,
                actor,
                actor
        ));
    }

    @Transactional
    public TestSuite runTests(String toolId) {
        Tool tool = requireTool(toolId);
        Version version = requireVersion(tool, tool.currentVersion());
        List<TestCase> enabled = repository.findTestCases(version.id()).stream()
                .filter(TestCase::enabled).toList();
        if (enabled.isEmpty()) {
            throw conflict("AI_TOOL_TEST_CASE_REQUIRED", "At least one enabled Tool test is required");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        List<TestCase> results = new ArrayList<>();
        int passed = 0;
        for (TestCase testCase : enabled) {
            Map<String, Object> input = parseMap(testCase.inputJson());
            ExecutorResult result = executeVersion(tool, version, input, new HashSet<>());
            String actual = json(result.output());
            boolean success = testCase.expectedResult() == null
                    || testCase.expectedResult().isBlank()
                    || jsonEquivalent(testCase.expectedResult(), actual)
                    || testCase.expectedResult().trim().equals(actual);
            if (success) {
                passed++;
            }
            TestCase updated = new TestCase(
                    testCase.id(), testCase.toolVersionId(), testCase.name(), testCase.inputJson(),
                    testCase.expectedResult(), testCase.enabled(), actual, success, now,
                    testCase.createTime(), now, testCase.createUser(), actor
            );
            repository.updateTestResult(updated);
            results.add(updated);
        }
        boolean allPassed = passed == enabled.size();
        repository.markVersionTested(version.id(), allPassed, now, actor);
        return new TestSuite(toolId, version.version(), enabled.size(), passed, allPassed, results, now);
    }

    @Transactional
    public Execution executePublished(String reference, Map<String, Object> input) {
        Tool tool = repository.findTool(reference)
                .or(() -> repository.findToolByCode(reference))
                .orElseThrow(() -> notFound("AI_TOOL_NOT_FOUND", "Tool not found"));
        if (tool.status() != Status.PUBLISHED || tool.publishedVersion() == null) {
            throw conflict("AI_TOOL_NOT_PUBLISHED", "Tool is not Published");
        }
        Version version = requireVersion(tool, tool.publishedVersion());
        Map<String, Object> normalized = validateInput(version, input == null ? Map.of() : input);
        Policy policy = repository.findPolicy(tool.id());
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String initialStatus = policy.manualConfirm()
                ? "WAITING_CONFIRM"
                : policy.approvalRequired() ? "WAITING_APPROVAL" : "RUNNING";
        Execution execution = repository.insertExecution(new Execution(
                UUID.randomUUID().toString(),
                tool.id(),
                version.version(),
                sanitize(normalized),
                null,
                hash(normalized),
                initialStatus,
                "PENDING",
                policy.manualConfirm() || policy.approvalRequired() ? UUID.randomUUID().toString() : null,
                policy.manualConfirm() ? null : actor,
                policy.approvalRequired() ? null : actor,
                0,
                null,
                null,
                requestContext.traceId(),
                now.plus(Math.max(1, policy.retentionDays()), ChronoUnit.DAYS),
                now,
                now,
                actor,
                actor
        ));
        if (!"RUNNING".equals(initialStatus)) {
            return execution;
        }
        return complete(execution, tool, version, normalized, actor);
    }

    @Transactional
    public Execution confirm(String executionId) {
        Execution execution = requireExecution(executionId);
        requireNotExpired(execution);
        if (!"WAITING_CONFIRM".equals(execution.status())) {
            throw conflict("AI_TOOL_CONFIRM_INVALID", "Execution is not waiting for confirmation");
        }
        Tool tool = requireTool(execution.toolId());
        Version version = requireVersion(tool, execution.toolVersion());
        Policy policy = repository.findPolicy(tool.id());
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Execution confirmed = copyExecution(
                execution,
                policy.approvalRequired() ? "WAITING_APPROVAL" : "RUNNING",
                "PENDING",
                null,
                actor,
                execution.approvedBy(),
                0,
                now,
                actor
        );
        repository.updateExecution(confirmed);
        return policy.approvalRequired()
                ? confirmed
                : complete(confirmed, tool, version, execution.request(), actor);
    }

    @Transactional
    public Execution approve(String executionId) {
        Execution execution = requireExecution(executionId);
        requireNotExpired(execution);
        if (!"WAITING_APPROVAL".equals(execution.status())) {
            throw conflict("AI_TOOL_APPROVAL_INVALID", "Execution is not waiting for approval");
        }
        Tool tool = requireTool(execution.toolId());
        Version version = requireVersion(tool, execution.toolVersion());
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Execution approved = copyExecution(
                execution, "RUNNING", "PENDING", null,
                execution.confirmedBy(), actor, 0, now, actor
        );
        repository.updateExecution(approved);
        return complete(approved, tool, version, execution.request(), actor);
    }

    @Transactional
    public View install(String marketId, String codeOverride) {
        MarketItem item = repository.findMarketItem(marketId)
                .orElseThrow(() -> notFound("AI_TOOL_MARKET_NOT_FOUND", "Marketplace Tool not found"));
        Map<String, Object> manifest = item.manifest();
        String code = codeOverride == null || codeOverride.isBlank() ? item.code() : codeOverride;
        Configuration configuration = new Configuration(
                item.name(),
                item.description(),
                item.category(),
                string(manifest.get("toolType"), "PLUGIN"),
                null,
                json(manifest.getOrDefault("schema", Map.of("type", "object"))),
                manifest.containsKey("outputSchema") ? json(manifest.get("outputSchema")) : null,
                string(manifest.get("executorType"), "MOCK"),
                map(manifest.get("executorConfig")),
                List.of(),
                List.of(),
                "Installed from " + item.publisher() + " " + item.version(),
                policySpec(map(manifest.get("policy")))
        );
        View created = create(code, configuration);
        repository.incrementInstall(item.id(), Instant.now(), requestContext.actor());
        return created;
    }

    @Transactional
    public View importOpenApi(String code, String openApiJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(openApiJson);
        } catch (JsonProcessingException exception) {
            throw invalid("AI_TOOL_OPENAPI_INVALID", "OpenAPI document is invalid JSON");
        }
        JsonNode info = root.path("info");
        String name = info.path("title").asText("Imported API");
        String description = info.path("description").asText("Imported from OpenAPI");
        String endpoint = root.path("servers").isArray() && !root.path("servers").isEmpty()
                ? root.path("servers").get(0).path("url").asText("")
                : "";
        return create(code, new Configuration(
                name,
                description,
                "HTTP",
                "REST",
                null,
                "{\"type\":\"object\"}",
                null,
                "HTTP",
                Map.of("endpoint", endpoint, "openApiImported", true),
                List.of(),
                List.of(),
                "OpenAPI import",
                new PolicySpec("READ_ONLY", true, false, false, 15, 0, false, 7)
        ));
    }

    private Execution complete(
            Execution source,
            Tool tool,
            Version version,
            Map<String, Object> input,
            String actor
    ) {
        Instant completed = Instant.now();
        try {
            ExecutorResult result = executeVersion(tool, version, input, new HashSet<>());
            if (version.outputSchemaJson() != null && !version.outputSchemaJson().isBlank()) {
                schemaValidator.validateOutput(version.outputSchemaJson(), json(result.output()));
            }
            Execution execution = copyExecution(
                    source,
                    "SUCCESS",
                    result.mode(),
                    sanitizeObject(result.output()),
                    source.confirmedBy(),
                    source.approvedBy(),
                    result.latencyMs(),
                    completed,
                    actor
            );
            repository.updateExecution(execution);
            recordAnalytics(execution, tool, result);
            return execution;
        } catch (RuntimeException exception) {
            Execution failed = new Execution(
                    source.id(), source.toolId(), source.toolVersion(), source.request(), null,
                    source.requestHash(), "FAILED", "ERROR", source.approvalToken(),
                    source.confirmedBy(), source.approvedBy(), 0,
                    exception instanceof ProviderOperationException operation
                            ? operation.errorCode() : "AI_TOOL_EXECUTION_FAILED",
                    trim(exception.getMessage(), 4000),
                    source.traceId(), source.expireTime(), source.createTime(), completed,
                    source.createUser(), actor
            );
            repository.updateExecution(failed);
            recordAnalytics(failed, tool, null);
            return failed;
        }
    }

    private ExecutorResult executeVersion(
            Tool tool,
            Version version,
            Map<String, Object> input,
            Set<String> visited
    ) {
        if (!visited.add(tool.id()) || visited.size() > 10) {
            throw conflict("AI_TOOL_CHAIN_CYCLE", "Tool chain contains a cycle or exceeds 10 steps");
        }
        ExecutorResult current = executionPort.execute(new ExecutorRequest(
                tool, version, input, requestContext.traceId()
        ));
        Object output = current.output();
        long latency = current.latencyMs();
        boolean executed = current.executed();
        String mode = current.mode();
        for (String reference : version.chain()) {
            Tool next = repository.findTool(reference)
                    .or(() -> repository.findToolByCode(reference))
                    .orElseThrow(() -> notFound("AI_TOOL_CHAIN_NOT_FOUND", "Tool chain reference not found"));
            if (next.publishedVersion() == null || next.status() != Status.PUBLISHED) {
                throw conflict("AI_TOOL_CHAIN_NOT_PUBLISHED", "Tool chain reference is not Published");
            }
            Version nextVersion = requireVersion(next, next.publishedVersion());
            ExecutorResult chained = executeVersion(
                    next,
                    nextVersion,
                    Map.of("input", input, "previous", output),
                    visited
            );
            output = chained.output();
            latency += chained.latencyMs();
            executed = executed && chained.executed();
            mode = executed ? "LOCAL" : "PREVIEW";
        }
        return new ExecutorResult(executed, mode, output, latency, current.metadata());
    }

    private Map<String, Object> validateInput(Version version, Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>(input);
        for (Parameter parameter : version.parameters()) {
            Object value = normalized.get(parameter.name());
            if (value == null && parameter.defaultValue() != null) {
                value = parseDefault(parameter);
                normalized.put(parameter.name(), value);
            }
            if (value == null && parameter.required()) {
                throw invalid("AI_TOOL_PARAMETER_REQUIRED", "Required Tool parameter is missing: " + parameter.name());
            }
            if (value != null) {
                validateType(parameter, value);
                validateRule(parameter, value);
            }
        }
        return normalized;
    }

    private void validateType(Parameter parameter, Object value) {
        boolean matches = switch (parameter.type()) {
            case "STRING" -> value instanceof String;
            case "INTEGER" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "OBJECT", "JSON" -> value instanceof Map<?, ?>;
            case "ARRAY", "LIST" -> value instanceof List<?>;
            default -> true;
        };
        if (!matches) {
            throw invalid(
                    "AI_TOOL_PARAMETER_TYPE_INVALID",
                    "Tool parameter type mismatch: " + parameter.name() + " expects " + parameter.type()
            );
        }
    }

    private void validateRule(Parameter parameter, Object value) {
        String rule = parameter.validationRule();
        if (rule == null || rule.isBlank()) {
            return;
        }
        if (rule.startsWith("regex:") && value instanceof String text
                && !Pattern.compile(rule.substring(6)).matcher(text).matches()) {
            throw invalid("AI_TOOL_PARAMETER_RULE_FAILED", "Tool parameter validation failed: " + parameter.name());
        }
        if (rule.startsWith("min:") && value instanceof Number number
                && number.doubleValue() < Double.parseDouble(rule.substring(4))) {
            throw invalid("AI_TOOL_PARAMETER_RULE_FAILED", "Tool parameter is below minimum: " + parameter.name());
        }
        if (rule.startsWith("max:") && value instanceof Number number
                && number.doubleValue() > Double.parseDouble(rule.substring(4))) {
            throw invalid("AI_TOOL_PARAMETER_RULE_FAILED", "Tool parameter exceeds maximum: " + parameter.name());
        }
    }

    private Version version(
            String toolId,
            String versionId,
            int version,
            Configuration configuration,
            Instant now,
            String actor
    ) {
        List<Parameter> parameters = configuration.parameters().stream()
                .map(spec -> new Parameter(
                        UUID.randomUUID().toString(),
                        versionId,
                        required(spec.name(), "Parameter name", 100),
                        required(spec.type(), "Parameter type", 32).toUpperCase(Locale.ROOT),
                        spec.required(),
                        trim(spec.defaultValue(), 100_000),
                        trim(spec.validationRule(), 1000),
                        trim(spec.description(), 1000),
                        now,
                        now,
                        actor,
                        actor
                )).toList();
        return new Version(
                versionId,
                toolId,
                version,
                configuration.schemaJson() == null || configuration.schemaJson().isBlank()
                        ? "{\"type\":\"object\"}" : configuration.schemaJson().trim(),
                trim(configuration.outputSchemaJson(), 200_000),
                required(configuration.executorType(), "Executor type", 40).toUpperCase(Locale.ROOT),
                configuration.executorConfig(),
                configuration.chain(),
                trim(configuration.changeLog(), 1000),
                false,
                null,
                null,
                parameters,
                now,
                now,
                actor,
                actor
        );
    }

    private Policy policy(String toolId, PolicySpec spec, Instant now, String actor) {
        PolicySpec value = spec == null
                ? new PolicySpec("READ_ONLY", true, false, false, 15, 0, false, 7)
                : spec;
        if (value.timeoutSeconds() < 1 || value.timeoutSeconds() > 300
                || value.retryCount() < 0 || value.retryCount() > 10
                || value.retentionDays() < 1 || value.retentionDays() > 3650) {
            throw invalid("AI_TOOL_POLICY_INVALID", "Tool timeout, retry, or retention policy is invalid");
        }
        return new Policy(
                UUID.randomUUID().toString(),
                toolId,
                required(value.accessLevel(), "Access level", 32).toUpperCase(Locale.ROOT),
                value.readonly(),
                value.manualConfirm(),
                value.approvalRequired(),
                value.timeoutSeconds(),
                value.retryCount(),
                value.logContent(),
                value.retentionDays(),
                now,
                now,
                actor,
                actor
        );
    }

    private void validateConfiguration(Configuration value) {
        if (value == null) {
            throw invalid("AI_TOOL_CONFIGURATION_REQUIRED", "Tool configuration is required");
        }
        String schema = value.schemaJson() == null || value.schemaJson().isBlank()
                ? "{\"type\":\"object\"}" : value.schemaJson();
        schemaValidator.validateSchema(schema);
        schemaValidator.validateSchema(value.outputSchemaJson());
        if (value.parameters() == null) {
            throw invalid("AI_TOOL_PARAMETERS_REQUIRED", "Tool parameters must be an array");
        }
        Set<String> names = new HashSet<>();
        for (ParameterSpec parameter : value.parameters()) {
            if (!names.add(required(parameter.name(), "Parameter name", 100))) {
                throw invalid("AI_TOOL_PARAMETER_DUPLICATE", "Tool parameter name is duplicated");
            }
        }
        if (value.chain() != null && value.chain().size() > 10) {
            throw invalid("AI_TOOL_CHAIN_TOO_LONG", "Tool chain cannot exceed 10 steps");
        }
    }

    private void recordAnalytics(Execution execution, Tool tool, ExecutorResult result) {
        analytics.record(new UsageEvent(
                UUID.randomUUID().toString(),
                execution.id(),
                execution.traceId(),
                "TOOL_EXECUTE",
                "TOOL",
                tool.id(),
                execution.updateUser(),
                null,
                null,
                null,
                null,
                null,
                Math.max(1, json(execution.request()).length() / 4L),
                result == null ? 0 : Math.max(1, json(result.output()).length() / 4L),
                0,
                BigDecimal.ZERO,
                null,
                execution.latencyMs(),
                execution.status(),
                execution.errorCode(),
                Map.of("toolCode", tool.code(), "mode", execution.mode()),
                execution.updateTime(),
                execution.updateUser()
        ));
    }

    private View view(Tool tool) {
        return new View(
                tool,
                requireVersion(tool, tool.currentVersion()),
                repository.findPolicy(tool.id())
        );
    }

    private Tool requireTool(String id) {
        return repository.findTool(id)
                .orElseThrow(() -> notFound("AI_TOOL_NOT_FOUND", "Tool not found"));
    }

    private Version requireVersion(Tool tool, int version) {
        return repository.findVersion(tool.id(), version)
                .orElseThrow(() -> notFound("AI_TOOL_VERSION_NOT_FOUND", "Tool version not found"));
    }

    private Execution requireExecution(String id) {
        return repository.findExecution(id)
                .orElseThrow(() -> notFound("AI_TOOL_EXECUTION_NOT_FOUND", "Tool execution not found"));
    }

    private void requireNotExpired(Execution execution) {
        if (execution.expireTime() != null && execution.expireTime().isBefore(Instant.now())) {
            throw new ProviderOperationException(
                    "AI_TOOL_EXECUTION_EXPIRED", "Tool execution approval window has expired", 410
            );
        }
    }

    private Tool copy(
            Tool value,
            Status status,
            int currentVersion,
            Integer publishedVersion,
            Instant now,
            String actor
    ) {
        return new Tool(
                value.id(), value.code(), value.name(), value.description(), value.category(),
                value.toolType(), value.icon(), value.ownerUser(), status, currentVersion,
                publishedVersion, value.createTime(), now, value.createUser(), actor
        );
    }

    private Execution copyExecution(
            Execution value,
            String status,
            String mode,
            Object response,
            String confirmedBy,
            String approvedBy,
            long latencyMs,
            Instant now,
            String actor
    ) {
        return new Execution(
                value.id(), value.toolId(), value.toolVersion(), value.request(), response,
                value.requestHash(), status, mode, value.approvalToken(), confirmedBy,
                approvedBy, latencyMs, null, null, value.traceId(), value.expireTime(),
                value.createTime(), now, value.createUser(), actor
        );
    }

    private Map<String, Object> sanitize(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(key, isSecret(key) ? "***" : sanitizeObject(value)));
        return result;
    }

    private Object sanitizeObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                String name = String.valueOf(key);
                result.put(name, isSecret(name) ? "***" : sanitizeObject(nested));
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeObject).toList();
        }
        return value;
    }

    private boolean isSecret(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return SECRET_KEYS.stream()
                .map(item -> item.toLowerCase(Locale.ROOT).replace("_", ""))
                .anyMatch(normalized::contains);
    }

    private Object parseDefault(Parameter parameter) {
        if ("STRING".equals(parameter.type())) {
            return parameter.defaultValue();
        }
        try {
            return objectMapper.readValue(parameter.defaultValue(), Object.class);
        } catch (JsonProcessingException exception) {
            throw invalid("AI_TOOL_PARAMETER_DEFAULT_INVALID", "Tool parameter default is invalid: " + parameter.name());
        }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw invalid("AI_TOOL_INPUT_INVALID", "Tool input must be a JSON object");
        }
    }

    private boolean jsonEquivalent(String expected, String actual) {
        try {
            return objectMapper.readTree(expected).equals(objectMapper.readTree(actual));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Tool data", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private PolicySpec policySpec(Map<String, Object> value) {
        return new PolicySpec(
                string(value.get("accessLevel"), "READ_ONLY"),
                booleanValue(value.get("readonly"), true),
                booleanValue(value.get("manualConfirm"), false),
                booleanValue(value.get("approvalRequired"), false),
                intValue(value.get("timeoutSeconds"), 15),
                intValue(value.get("retryCount"), 0),
                booleanValue(value.get("logContent"), false),
                intValue(value.get("retentionDays"), 7)
        );
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String normalizeCode(String code) {
        String normalized = required(code, "Tool code", 100).toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw invalid("AI_TOOL_CODE_INVALID", "Tool code must use lowercase letters, digits, dot, dash, or underscore");
        }
        return normalized;
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_TOOL_FIELD_REQUIRED", label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw invalid("AI_TOOL_FIELD_TOO_LONG", label + " exceeds " + max + " characters");
        }
        return trimmed;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private void invalidTransition(Status from, Status to) {
        throw conflict("AI_TOOL_STATUS_INVALID", "Invalid Tool transition: " + from + " -> " + to);
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException notFound(String code, String message) {
        return new ProviderOperationException(code, message, 404);
    }

    public record Configuration(
            String name,
            String description,
            String category,
            String toolType,
            String icon,
            String schemaJson,
            String outputSchemaJson,
            String executorType,
            Map<String, Object> executorConfig,
            List<ParameterSpec> parameters,
            List<String> chain,
            String changeLog,
            PolicySpec policy
    ) {
        public Configuration {
            executorConfig = executorConfig == null ? Map.of() : Map.copyOf(executorConfig);
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            chain = chain == null ? List.of() : List.copyOf(chain);
        }
    }

    public record ParameterSpec(
            String name,
            String type,
            boolean required,
            String defaultValue,
            String validationRule,
            String description
    ) {
    }

    public record PolicySpec(
            String accessLevel,
            boolean readonly,
            boolean manualConfirm,
            boolean approvalRequired,
            int timeoutSeconds,
            int retryCount,
            boolean logContent,
            int retentionDays
    ) {
    }
}
