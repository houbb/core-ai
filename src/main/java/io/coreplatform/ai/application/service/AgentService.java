package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AgentModels.Agent;
import io.coreplatform.ai.application.domain.AgentModels.Approval;
import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Execution;
import io.coreplatform.ai.application.domain.AgentModels.ExecutionView;
import io.coreplatform.ai.application.domain.AgentModels.KnowledgeBinding;
import io.coreplatform.ai.application.domain.AgentModels.MemoryPolicy;
import io.coreplatform.ai.application.domain.AgentModels.Planner;
import io.coreplatform.ai.application.domain.AgentModels.Profile;
import io.coreplatform.ai.application.domain.AgentModels.Schedule;
import io.coreplatform.ai.application.domain.AgentModels.Task;
import io.coreplatform.ai.application.domain.AgentModels.ToolBinding;
import io.coreplatform.ai.application.domain.AgentModels.TraceStep;
import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;
import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchResult;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.AgentPlannerPort;
import io.coreplatform.ai.application.port.AgentRepository;
import io.coreplatform.ai.application.port.AnalyticsEventPort;
import io.coreplatform.ai.application.port.GatewayInvocationPort;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");

    private final AgentRepository repository;
    private final AgentPlannerPort plannerPort;
    private final GatewayInvocationPort gateway;
    private final ToolService toolService;
    private final KnowledgeService knowledgeService;
    private final ConversationService conversationService;
    private final AnalyticsEventPort analytics;
    private final RequestContextPort requestContext;
    private final ObjectMapper objectMapper;

    public AgentService(
            AgentRepository repository,
            AgentPlannerPort plannerPort,
            GatewayInvocationPort gateway,
            ToolService toolService,
            KnowledgeService knowledgeService,
            ConversationService conversationService,
            AnalyticsEventPort analytics,
            RequestContextPort requestContext,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.plannerPort = plannerPort;
        this.gateway = gateway;
        this.toolService = toolService;
        this.knowledgeService = knowledgeService;
        this.conversationService = conversationService;
        this.analytics = analytics;
        this.requestContext = requestContext;
        this.objectMapper = objectMapper;
    }

    public List<Agent> search(String query) {
        String actor = requestContext.actor();
        return repository.search(query).stream()
                .filter(agent -> agent.ownerUser().equals(actor) || "PUBLISHED".equals(agent.status()))
                .toList();
    }

    public Definition get(String id) {
        Definition definition = repository.findDefinition(id);
        requireRead(definition.agent());
        return definition;
    }

    @Transactional
    public Definition create(String code, Configuration configuration) {
        String normalizedCode = normalizeCode(code);
        if (repository.existsByCode(normalizedCode)) {
            throw conflict("AI_AGENT_CODE_EXISTS", "Agent code already exists");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String agentId = UUID.randomUUID().toString();
        Definition definition = definition(
                agentId, normalizedCode, 1, null, now, now, actor, actor, configuration
        );
        repository.insertDefinition(definition, json(definition));
        return definition;
    }

    @Transactional
    public Definition update(String id, Configuration configuration) {
        Definition current = repository.findDefinition(id);
        requireOwner(current.agent());
        if ("ARCHIVED".equals(current.agent().status())) {
            throw conflict("AI_AGENT_ARCHIVED", "Archived Agent cannot be edited");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Definition updated = definition(
                current.agent().id(),
                current.agent().code(),
                current.agent().currentVersion() + 1,
                current.agent().publishedVersion(),
                current.agent().createTime(),
                now,
                current.agent().createUser(),
                actor,
                configuration
        );
        repository.updateDefinition(updated, json(updated));
        return updated;
    }

    @Transactional
    public Map<String, Object> test(String id) {
        Definition definition = repository.findDefinition(id);
        requireOwner(definition.agent());
        List<String> errors = validateDefinition(definition);
        boolean passed = errors.isEmpty();
        repository.markVersionTested(
                id,
                definition.agent().currentVersion(),
                passed,
                Instant.now(),
                requestContext.actor()
        );
        return Map.of(
                "agentId", id,
                "version", definition.agent().currentVersion(),
                "passed", passed,
                "plannerMode", plannerPort.mode(),
                "taskCount", definition.tasks().stream().filter(Task::enabled).count(),
                "errors", errors
        );
    }

    @Transactional
    public Definition transition(String id, String targetStatus) {
        Definition definition = repository.findDefinition(id);
        Agent current = definition.agent();
        requireOwner(current);
        String target = required(targetStatus, "Agent status", 32).toUpperCase(Locale.ROOT);
        Integer published = current.publishedVersion();
        switch (current.status()) {
            case "DRAFT" -> {
                if (!List.of("TESTING", "ARCHIVED").contains(target)) {
                    invalidTransition(current.status(), target);
                }
            }
            case "TESTING" -> {
                if ("PUBLISHED".equals(target)) {
                    if (!repository.isVersionTested(current.id(), current.currentVersion())) {
                        throw conflict("AI_AGENT_TEST_REQUIRED", "Run and pass Agent tests before publishing");
                    }
                    repository.markVersionPublished(
                            current.id(), current.currentVersion(), Instant.now(), requestContext.actor()
                    );
                    published = current.currentVersion();
                } else if (!List.of("DRAFT", "ARCHIVED").contains(target)) {
                    invalidTransition(current.status(), target);
                }
            }
            case "PUBLISHED" -> {
                if (!List.of("DISABLED", "ARCHIVED").contains(target)) {
                    invalidTransition(current.status(), target);
                }
            }
            case "DISABLED" -> {
                if (!List.of("PUBLISHED", "ARCHIVED").contains(target)) {
                    invalidTransition(current.status(), target);
                }
                if ("PUBLISHED".equals(target) && published == null) {
                    throw conflict("AI_AGENT_NO_PUBLISHED_VERSION", "Agent has no Published version");
                }
            }
            case "ARCHIVED" -> invalidTransition(current.status(), target);
            default -> throw conflict("AI_AGENT_STATUS_INVALID", "Unknown Agent status");
        }
        if ("ARCHIVED".equals(target)) {
            published = null;
        }
        Agent updated = copyAgent(
                current, target, current.currentVersion(), published, Instant.now(), requestContext.actor()
        );
        repository.updateAgent(updated);
        return new Definition(
                updated, definition.profile(), definition.planner(), definition.tasks(),
                definition.tools(), definition.knowledge(), definition.memory()
        );
    }

    @Transactional
    public ExecutionView execute(String reference, String goal, String conversationId) {
        Agent agent = repository.findAgent(reference)
                .or(() -> repository.findAgentByCode(reference))
                .orElseThrow(() -> notFound("AI_AGENT_NOT_FOUND", "Agent not found"));
        requireRead(agent);
        if (!"PUBLISHED".equals(agent.status()) || agent.publishedVersion() == null) {
            throw conflict("AI_AGENT_NOT_PUBLISHED", "Agent is not Published");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Execution execution = repository.insertExecution(new Execution(
                UUID.randomUUID().toString(),
                agent.id(),
                agent.publishedVersion(),
                trim(conversationId, 64),
                required(goal, "Execution goal", 1_000_000),
                "RUNNING",
                0,
                null,
                null,
                requestContext.traceId(),
                now,
                null,
                now,
                now,
                actor,
                actor
        ));
        trace(execution.id(), "PLANNER", plannerPort.mode(), "Plan created", "SUCCESS", 0,
                Map.of("goal", compact(goal)));
        return continueExecution(execution, null, false);
    }

    public ExecutionView execution(String id) {
        Execution execution = requireExecution(id);
        Agent agent = repository.findAgent(execution.agentId())
                .orElseThrow(() -> notFound("AI_AGENT_NOT_FOUND", "Agent not found"));
        requireRead(agent);
        return view(execution);
    }

    public List<Execution> executions(String agentId, int limit) {
        Agent agent = repository.findAgent(agentId)
                .orElseThrow(() -> notFound("AI_AGENT_NOT_FOUND", "Agent not found"));
        requireRead(agent);
        return repository.findExecutions(agentId, limit);
    }

    @Transactional
    public ExecutionView approve(String executionId, boolean approved, String reason) {
        Execution execution = requireExecution(executionId);
        Approval pending = repository.findPendingApproval(executionId)
                .orElseThrow(() -> conflict("AI_AGENT_APPROVAL_NOT_PENDING", "Agent has no pending approval"));
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Approval decided = new Approval(
                pending.id(), pending.executionId(), pending.taskId(), pending.approvalType(),
                pending.requestDetail(), approved ? "APPROVED" : "REJECTED",
                pending.approvalToken(), approved ? actor : null, approved ? now : null,
                approved ? null : trim(reason, 1000), pending.createTime(), now
        );
        repository.updateApproval(decided);
        if (!approved) {
            Execution rejected = copyExecution(
                    execution, "REJECTED", execution.currentTaskNo(), "Approval rejected",
                    "AI_AGENT_APPROVAL_REJECTED", now, actor
            );
            repository.updateExecution(rejected);
            return view(rejected);
        }
        if (pending.approvalType().startsWith("TOOL_RUNTIME_")) {
            completeToolApproval(pending);
            return continueExecution(execution, pending.taskId(), true);
        }
        return continueExecution(execution, pending.taskId(), false);
    }

    @Transactional
    public ExecutionView pause(String executionId) {
        Execution current = requireExecution(executionId);
        if (!List.of("RUNNING", "WAITING_APPROVAL").contains(current.status())) {
            throw conflict("AI_AGENT_PAUSE_INVALID", "Agent execution cannot be paused in current status");
        }
        Execution paused = copyExecution(
                current, "PAUSED", current.currentTaskNo(), current.result(), null,
                Instant.now(), requestContext.actor()
        );
        repository.updateExecution(paused);
        return view(paused);
    }

    @Transactional
    public ExecutionView resume(String executionId) {
        Execution current = requireExecution(executionId);
        if (!"PAUSED".equals(current.status())) {
            throw conflict("AI_AGENT_RESUME_INVALID", "Agent execution is not paused");
        }
        return continueExecution(current, null, false);
    }

    @Transactional
    public Schedule saveSchedule(
            String agentId,
            String scheduleId,
            String cron,
            String goalTemplate,
            boolean enabled
    ) {
        Agent agent = repository.findAgent(agentId)
                .orElseThrow(() -> notFound("AI_AGENT_NOT_FOUND", "Agent not found"));
        requireOwner(agent);
        CronExpression expression;
        try {
            expression = CronExpression.parse(required(cron, "Cron expression", 100));
        } catch (IllegalArgumentException exception) {
            throw invalid("AI_AGENT_CRON_INVALID", "Agent Cron expression is invalid");
        }
        Instant now = Instant.now();
        ZonedDateTime next = expression.next(now.atZone(ZoneOffset.UTC));
        Schedule current = scheduleId == null ? null : repository.findSchedules(agentId).stream()
                .filter(item -> item.id().equals(scheduleId)).findFirst().orElse(null);
        return repository.saveSchedule(new Schedule(
                scheduleId == null || scheduleId.isBlank() ? UUID.randomUUID().toString() : scheduleId,
                agentId,
                cron,
                required(goalTemplate, "Goal template", 1_000_000),
                enabled,
                enabled && next != null ? next.toInstant() : null,
                current == null ? null : current.lastRunTime(),
                current == null ? now : current.createTime(),
                now
        ));
    }

    public List<Schedule> schedules(String agentId) {
        Agent agent = repository.findAgent(agentId)
                .orElseThrow(() -> notFound("AI_AGENT_NOT_FOUND", "Agent not found"));
        requireRead(agent);
        return repository.findSchedules(agentId);
    }

    @Scheduled(fixedDelayString = "${core.agent.schedule-delay-ms:30000}")
    public void runDueSchedules() {
        Instant now = Instant.now();
        for (Schedule schedule : repository.findDueSchedules(now)) {
            try {
                execute(schedule.agentId(), schedule.goalTemplate(), null);
                CronExpression expression = CronExpression.parse(schedule.cronExpression());
                ZonedDateTime next = expression.next(now.atZone(ZoneOffset.UTC));
                repository.saveSchedule(new Schedule(
                        schedule.id(), schedule.agentId(), schedule.cronExpression(),
                        schedule.goalTemplate(), schedule.enabled(),
                        next == null ? null : next.toInstant(), now,
                        schedule.createTime(), Instant.now()
                ));
            } catch (RuntimeException ignored) {
                // Scheduled Agent failures are persisted by the execution path and never stop the scheduler.
            }
        }
    }

    private ExecutionView continueExecution(
            Execution source,
            String approvedTaskId,
            boolean skipApprovedToolTask
    ) {
        Definition definition = repository.findDefinition(source.agentId());
        List<Task> plan = plannerPort.plan(definition, source.goal());
        Execution current = new Execution(
                source.id(), source.agentId(), source.agentVersion(), source.conversationId(),
                source.goal(), "RUNNING", source.currentTaskNo(), source.result(), null,
                source.traceId(), source.startedAt() == null ? Instant.now() : source.startedAt(),
                null, source.createTime(), Instant.now(), source.createUser(), requestContext.actor()
        );
        repository.updateExecution(current);
        String accumulated = current.result() == null ? "" : current.result();
        for (Task task : plan) {
            if (task.orderNo() < current.currentTaskNo()
                    || task.orderNo() == current.currentTaskNo() && skipApprovedToolTask) {
                continue;
            }
            if (needsApproval(definition, task) && !task.id().equals(approvedTaskId)) {
                return waitForApproval(current, task, "AGENT_POLICY", json(Map.of(
                        "task", task.name(),
                        "type", task.taskType(),
                        "reference", task.referenceId() == null ? "" : task.referenceId()
                )));
            }
            long started = System.nanoTime();
            try {
                TaskResult result = runTask(definition, current, task, accumulated);
                long latency = Math.max(result.latencyMs(), (System.nanoTime() - started) / 1_000_000L);
                trace(current.id(), task.taskType(), task.name(), result.output(), result.status(), latency,
                        result.metadata());
                if (result.pendingToolExecution() != null) {
                    String approvalType = "WAITING_CONFIRM".equals(result.pendingToolExecution().status())
                            ? "TOOL_RUNTIME_CONFIRM" : "TOOL_RUNTIME_APPROVE";
                    return waitForApproval(
                            current,
                            task,
                            approvalType,
                            result.pendingToolExecution().id()
                    );
                }
                accumulated = append(accumulated, task.name(), result.output());
                current = new Execution(
                        current.id(), current.agentId(), current.agentVersion(), current.conversationId(),
                        current.goal(), "RUNNING", task.orderNo() + 1, accumulated, null,
                        current.traceId(), current.startedAt(), null, current.createTime(), Instant.now(),
                        current.createUser(), requestContext.actor()
                );
                repository.updateExecution(current);
            } catch (RuntimeException exception) {
                trace(current.id(), task.taskType(), task.name(), exception.getMessage(), "FAILED",
                        (System.nanoTime() - started) / 1_000_000L, Map.of());
                if (task.config().get("optional") instanceof Boolean optional && optional) {
                    continue;
                }
                Execution failed = copyExecution(
                        current, "FAILED", task.orderNo(), accumulated,
                        exception instanceof ProviderOperationException operation
                                ? operation.errorCode() : "AI_AGENT_TASK_FAILED",
                        Instant.now(), requestContext.actor()
                );
                repository.updateExecution(failed);
                recordAnalytics(failed);
                return view(failed);
            }
        }
        Execution completed = new Execution(
                current.id(), current.agentId(), current.agentVersion(), current.conversationId(),
                current.goal(), "SUCCESS", current.currentTaskNo(),
                accumulated.isBlank() ? "Agent completed without output." : accumulated,
                null, current.traceId(), current.startedAt(), Instant.now(), current.createTime(),
                Instant.now(), current.createUser(), requestContext.actor()
        );
        repository.updateExecution(completed);
        recordAnalytics(completed);
        return view(completed);
    }

    private TaskResult runTask(
            Definition definition,
            Execution execution,
            Task task,
            String previous
    ) {
        return switch (task.taskType()) {
            case "TOOL" -> {
                io.coreplatform.ai.application.domain.ToolModels.Execution toolExecution =
                        toolService.executePublished(
                                required(task.referenceId(), "Tool reference", 100),
                                Map.of("goal", execution.goal(), "previous", previous)
                        );
                if ("FAILED".equals(toolExecution.status())) {
                    throw new ProviderOperationException(
                            toolExecution.errorCode() == null
                                    ? "AI_AGENT_TOOL_FAILED" : toolExecution.errorCode(),
                            toolExecution.errorMessage() == null
                                    ? "Agent Tool execution failed" : toolExecution.errorMessage(),
                            502
                    );
                }
                boolean pending = List.of("WAITING_CONFIRM", "WAITING_APPROVAL")
                        .contains(toolExecution.status());
                yield new TaskResult(
                        pending ? "Tool is waiting for human approval" : json(toolExecution.response()),
                        toolExecution.latencyMs(),
                        toolExecution.status(),
                        Map.of("toolExecutionId", toolExecution.id(), "mode", toolExecution.mode()),
                        pending ? toolExecution : null
                );
            }
            case "KNOWLEDGE" -> {
                SearchResult result = knowledgeService.retrieve(
                        required(task.referenceId(), "Knowledge reference", 100),
                        execution.goal(),
                        intValue(task.config().get("topK"), 5)
                );
                String output = result.hits().stream()
                        .map(hit -> hit.citation() + " " + hit.content())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("No relevant knowledge found.");
                yield new TaskResult(
                        output, result.latencyMs(), "SUCCESS",
                        Map.of("traceId", result.traceId(), "hitCount", result.hits().size()), null
                );
            }
            case "MEMORY" -> {
                if (!definition.memory().writeEnabled()) {
                    yield new TaskResult("Memory write skipped by policy", 0, "SKIPPED", Map.of(), null);
                }
                conversationService.saveMemory(
                        null,
                        "USER",
                        requestContext.actor(),
                        "AGENT_RESULT",
                        previous.isBlank() ? execution.goal() : previous,
                        0.6,
                        "AGENT:" + definition.agent().code(),
                        Map.of("executionId", execution.id())
                );
                yield new TaskResult("Agent result saved to User Memory", 0, "SUCCESS", Map.of(), null);
            }
            case "WAIT" -> new TaskResult(
                    "Wait step previewed; no thread was blocked",
                    0,
                    "PREVIEW",
                    Map.of("durationMs", intValue(task.config().get("durationMs"), 0)),
                    null
            );
            default -> {
                InvocationResult result = gateway.invoke(new Invocation(
                        UUID.randomUUID().toString(),
                        execution.traceId(),
                        definition.agent().sceneCode(),
                        task.referenceId() == null || task.referenceId().isBlank()
                                ? definition.agent().sceneCode() == null
                                ? "agent-default" : definition.agent().sceneCode()
                                : task.referenceId(),
                        agentPrompt(definition, execution.goal(), previous, task),
                        task.config(),
                        false,
                        false,
                        requestContext.actor()
                ));
                yield new TaskResult(
                        result.output(), result.latencyMs(), result.status(),
                        Map.of("gatewayMode", result.mode(), "executed", result.executed()), null
                );
            }
        };
    }

    private ExecutionView waitForApproval(
            Execution current,
            Task task,
            String approvalType,
            String detail
    ) {
        Instant now = Instant.now();
        repository.insertApproval(new Approval(
                UUID.randomUUID().toString(),
                current.id(),
                task.id(),
                approvalType,
                detail,
                "PENDING",
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                now,
                now
        ));
        Execution waiting = new Execution(
                current.id(), current.agentId(), current.agentVersion(), current.conversationId(),
                current.goal(), "WAITING_APPROVAL", task.orderNo(), current.result(), null,
                current.traceId(), current.startedAt(), null, current.createTime(), now,
                current.createUser(), requestContext.actor()
        );
        repository.updateExecution(waiting);
        trace(current.id(), "APPROVAL", task.name(), detail, "PENDING", 0,
                Map.of("approvalType", approvalType));
        return view(waiting);
    }

    private void completeToolApproval(Approval approval) {
        if ("TOOL_RUNTIME_CONFIRM".equals(approval.approvalType())) {
            io.coreplatform.ai.application.domain.ToolModels.Execution confirmed =
                    toolService.confirm(approval.requestDetail());
            if ("WAITING_APPROVAL".equals(confirmed.status())) {
                toolService.approve(approval.requestDetail());
            }
        } else {
            toolService.approve(approval.requestDetail());
        }
    }

    private boolean needsApproval(Definition definition, Task task) {
        return "TOOL".equals(task.taskType()) && definition.tools().stream()
                .filter(binding -> binding.toolId().equals(task.referenceId()))
                .anyMatch(ToolBinding::approvalRequired);
    }

    private List<String> validateDefinition(Definition definition) {
        List<String> errors = new ArrayList<>();
        if (definition.tasks().stream().noneMatch(Task::enabled)) {
            errors.add("At least one enabled task is required");
        }
        if (definition.tasks().stream().map(Task::orderNo).distinct().count() != definition.tasks().size()) {
            errors.add("Task order numbers must be unique");
        }
        if (definition.planner().maxSteps() < definition.tasks().stream().filter(Task::enabled).count()) {
            errors.add("Planner maxSteps is lower than enabled task count");
        }
        for (Task task : definition.tasks()) {
            if (List.of("TOOL", "KNOWLEDGE").contains(task.taskType())
                    && (task.referenceId() == null || task.referenceId().isBlank())) {
                errors.add(task.name() + " requires a reference");
            }
        }
        return List.copyOf(errors);
    }

    private Definition definition(
            String agentId,
            String code,
            int version,
            Integer publishedVersion,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser,
            Configuration configuration
    ) {
        validateConfiguration(configuration);
        Agent agent = new Agent(
                agentId,
                code,
                required(configuration.name(), "Agent name", 200),
                trim(configuration.description(), 4000),
                "DRAFT",
                createUser,
                trim(configuration.sceneCode(), 100),
                trim(configuration.icon(), 40),
                trim(configuration.color(), 40),
                new LinkedHashSet<>(configuration.tags()).stream().limit(30).toList(),
                version,
                publishedVersion,
                createTime,
                updateTime,
                createUser,
                updateUser
        );
        ProfileSpec profile = configuration.profile();
        Profile agentProfile = new Profile(
                UUID.randomUUID().toString(),
                agentId,
                required(profile.role(), "Agent role", 1000),
                required(profile.goal(), "Agent goal", 100_000),
                trim(profile.personality(), 1000),
                trim(profile.style(), 1000),
                trim(profile.language(), 40),
                trim(profile.constraints(), 100_000)
        );
        PlannerSpec planner = configuration.planner();
        Planner agentPlanner = new Planner(
                UUID.randomUUID().toString(),
                agentId,
                planner.type() == null || planner.type().isBlank()
                        ? "DETERMINISTIC" : planner.type().toUpperCase(Locale.ROOT),
                planner.config(),
                planner.maxSteps(),
                planner.maxDepth(),
                planner.retryCount()
        );
        List<Task> tasks = configuration.tasks().stream().map(spec -> new Task(
                UUID.randomUUID().toString(),
                agentId,
                required(spec.name(), "Task name", 200),
                spec.orderNo(),
                required(spec.type(), "Task type", 40).toUpperCase(Locale.ROOT),
                trim(spec.referenceId(), 100),
                spec.executionMode() == null || spec.executionMode().isBlank()
                        ? "SERIAL" : spec.executionMode().toUpperCase(Locale.ROOT),
                spec.condition(),
                spec.config(),
                spec.enabled()
        )).toList();
        return new Definition(
                agent,
                agentProfile,
                agentPlanner,
                tasks,
                configuration.tools(),
                configuration.knowledge(),
                configuration.memory()
        );
    }

    private void validateConfiguration(Configuration value) {
        if (value == null || value.profile() == null || value.planner() == null) {
            throw invalid("AI_AGENT_CONFIGURATION_REQUIRED", "Agent Profile and Planner are required");
        }
        if (value.planner().maxSteps() < 1 || value.planner().maxSteps() > 100
                || value.planner().maxDepth() < 1 || value.planner().maxDepth() > 20
                || value.planner().retryCount() < 0 || value.planner().retryCount() > 10) {
            throw invalid("AI_AGENT_PLANNER_INVALID", "Agent Planner limits are invalid");
        }
        if (value.tasks().size() > 100) {
            throw invalid("AI_AGENT_TASK_LIMIT", "Agent cannot contain more than 100 tasks");
        }
    }

    private void trace(
            String executionId,
            String stage,
            String action,
            String result,
            String status,
            long latency,
            Map<String, Object> metadata
    ) {
        int stepNo = repository.findTrace(executionId).size() + 1;
        repository.insertTrace(new TraceStep(
                UUID.randomUUID().toString(),
                executionId,
                stepNo,
                stage,
                action,
                trim(result, 100_000),
                status,
                Math.max(0, latency),
                metadata,
                Instant.now()
        ));
    }

    private void recordAnalytics(Execution execution) {
        analytics.record(new UsageEvent(
                UUID.randomUUID().toString(),
                execution.id(),
                execution.traceId(),
                "AGENT_EXECUTE",
                "AGENT",
                execution.agentId(),
                execution.updateUser(),
                null,
                null,
                null,
                null,
                null,
                Math.max(1, execution.goal().length() / 4L),
                execution.result() == null ? 0 : Math.max(1, execution.result().length() / 4L),
                0,
                BigDecimal.ZERO,
                null,
                execution.startedAt() == null || execution.endedAt() == null
                        ? 0 : java.time.Duration.between(execution.startedAt(), execution.endedAt()).toMillis(),
                execution.status(),
                execution.errorCode(),
                Map.of("version", execution.agentVersion(), "taskNo", execution.currentTaskNo()),
                execution.updateTime(),
                execution.updateUser()
        ));
    }

    private ExecutionView view(Execution execution) {
        return new ExecutionView(
                execution,
                repository.findTrace(execution.id()),
                repository.findPendingApproval(execution.id()).orElse(null)
        );
    }

    private Execution requireExecution(String id) {
        return repository.findExecution(id)
                .orElseThrow(() -> notFound("AI_AGENT_EXECUTION_NOT_FOUND", "Agent execution not found"));
    }

    private void requireRead(Agent agent) {
        if (!agent.ownerUser().equals(requestContext.actor()) && !"PUBLISHED".equals(agent.status())) {
            throw new ProviderOperationException("AI_AGENT_FORBIDDEN", "Agent is not accessible", 403);
        }
    }

    private void requireOwner(Agent agent) {
        if (!agent.ownerUser().equals(requestContext.actor())) {
            throw new ProviderOperationException("AI_AGENT_MANAGE_FORBIDDEN", "Agent cannot be managed", 403);
        }
    }

    private Agent copyAgent(
            Agent value,
            String status,
            int currentVersion,
            Integer publishedVersion,
            Instant now,
            String actor
    ) {
        return new Agent(
                value.id(), value.code(), value.name(), value.description(), status,
                value.ownerUser(), value.sceneCode(), value.icon(), value.color(), value.tags(),
                currentVersion, publishedVersion, value.createTime(), now, value.createUser(), actor
        );
    }

    private Execution copyExecution(
            Execution value,
            String status,
            int currentTask,
            String result,
            String errorCode,
            Instant now,
            String actor
    ) {
        boolean ended = List.of("SUCCESS", "FAILED", "REJECTED").contains(status);
        return new Execution(
                value.id(), value.agentId(), value.agentVersion(), value.conversationId(),
                value.goal(), status, currentTask, result, errorCode, value.traceId(),
                value.startedAt(), ended ? now : null, value.createTime(), now,
                value.createUser(), actor
        );
    }

    private String agentPrompt(Definition definition, String goal, String previous, Task task) {
        return "Role: " + definition.profile().role()
                + "\nAgent goal: " + definition.profile().goal()
                + "\nExecution goal: " + goal
                + "\nTask: " + task.name()
                + "\nPrevious result: " + (previous.isBlank() ? "none" : previous)
                + "\nConstraints: " + (definition.profile().constraints() == null
                ? "none" : definition.profile().constraints());
    }

    private String append(String current, String task, String output) {
        return (current.isBlank() ? "" : current + "\n\n") + "## " + task + "\n" + output;
    }

    private String compact(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 297) + "...";
    }

    private String normalizeCode(String code) {
        String normalized = required(code, "Agent code", 100).toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw invalid("AI_AGENT_CODE_INVALID", "Agent code format is invalid");
        }
        return normalized;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Agent data", exception);
        }
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_AGENT_FIELD_REQUIRED", label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw invalid("AI_AGENT_FIELD_TOO_LONG", label + " exceeds " + max + " characters");
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

    private void invalidTransition(String from, String to) {
        throw conflict("AI_AGENT_STATUS_INVALID", "Invalid Agent transition: " + from + " -> " + to);
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

    private record TaskResult(
            String output,
            long latencyMs,
            String status,
            Map<String, Object> metadata,
            io.coreplatform.ai.application.domain.ToolModels.Execution pendingToolExecution
    ) {
    }

    public record Configuration(
            String name,
            String description,
            String sceneCode,
            String icon,
            String color,
            List<String> tags,
            ProfileSpec profile,
            PlannerSpec planner,
            List<TaskSpec> tasks,
            List<ToolBinding> tools,
            List<KnowledgeBinding> knowledge,
            MemoryPolicy memory
    ) {
        public Configuration {
            tags = tags == null ? List.of() : List.copyOf(tags);
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            tools = tools == null ? List.of() : List.copyOf(tools);
            knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
            memory = memory == null
                    ? new MemoryPolicy("NONE", List.of(), false, 0, Map.of()) : memory;
        }
    }

    public record ProfileSpec(
            String role,
            String goal,
            String personality,
            String style,
            String language,
            String constraints
    ) {
    }

    public record PlannerSpec(
            String type,
            Map<String, Object> config,
            int maxSteps,
            int maxDepth,
            int retryCount
    ) {
        public PlannerSpec {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record TaskSpec(
            String name,
            int orderNo,
            String type,
            String referenceId,
            String executionMode,
            Map<String, Object> condition,
            Map<String, Object> config,
            boolean enabled
    ) {
        public TaskSpec {
            condition = condition == null ? Map.of() : Map.copyOf(condition);
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }
}
