package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.AgentModels.Agent;
import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Execution;
import io.coreplatform.ai.application.domain.AgentModels.ExecutionView;
import io.coreplatform.ai.application.domain.AgentModels.KnowledgeBinding;
import io.coreplatform.ai.application.domain.AgentModels.MemoryPolicy;
import io.coreplatform.ai.application.domain.AgentModels.Schedule;
import io.coreplatform.ai.application.domain.AgentModels.ToolBinding;
import io.coreplatform.ai.application.service.AgentService;
import io.coreplatform.ai.application.service.AgentService.Configuration;
import io.coreplatform.ai.application.service.AgentService.PlannerSpec;
import io.coreplatform.ai.application.service.AgentService.ProfileSpec;
import io.coreplatform.ai.application.service.AgentService.TaskSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/ai/admin/agents")
    public List<Agent> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    @PostMapping("/api/v1/ai/admin/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public Definition create(@Valid @RequestBody AgentRequest request) {
        return service.create(request.code(), configuration(request));
    }

    @GetMapping("/api/v1/ai/admin/agents/{id}")
    public Definition get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/ai/admin/agents/{id}")
    public Definition update(@PathVariable String id, @Valid @RequestBody AgentRequest request) {
        return service.update(id, configuration(request));
    }

    @PostMapping("/api/v1/ai/admin/agents/{id}/tests/run")
    public Map<String, Object> test(@PathVariable String id) {
        return service.test(id);
    }

    @PatchMapping("/api/v1/ai/admin/agents/{id}/status")
    public Definition status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return service.transition(id, request.status());
    }

    @GetMapping("/api/v1/ai/admin/agents/{id}/executions")
    public List<Execution> executions(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.executions(id, limit);
    }

    @PostMapping("/api/v1/ai/agents/{reference}/execute")
    public ExecutionView execute(
            @PathVariable String reference,
            @Valid @RequestBody ExecuteRequest request
    ) {
        return service.execute(reference, request.goal(), request.conversationId());
    }

    @GetMapping("/api/v1/ai/agent-executions/{id}")
    public ExecutionView execution(@PathVariable String id) {
        return service.execution(id);
    }

    @PostMapping("/api/v1/ai/agent-executions/{id}/approval")
    public ExecutionView approval(
            @PathVariable String id,
            @RequestBody ApprovalRequest request
    ) {
        return service.approve(id, request.approved(), request.reason());
    }

    @PostMapping("/api/v1/ai/agent-executions/{id}/pause")
    public ExecutionView pause(@PathVariable String id) {
        return service.pause(id);
    }

    @PostMapping("/api/v1/ai/agent-executions/{id}/resume")
    public ExecutionView resume(@PathVariable String id) {
        return service.resume(id);
    }

    @GetMapping("/api/v1/ai/admin/agents/{id}/schedules")
    public List<Schedule> schedules(@PathVariable String id) {
        return service.schedules(id);
    }

    @PostMapping("/api/v1/ai/admin/agents/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public Schedule createSchedule(
            @PathVariable String id,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return service.saveSchedule(
                id, null, request.cronExpression(), request.goalTemplate(), request.enabled()
        );
    }

    @PutMapping("/api/v1/ai/admin/agents/{id}/schedules/{scheduleId}")
    public Schedule updateSchedule(
            @PathVariable String id,
            @PathVariable String scheduleId,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return service.saveSchedule(
                id, scheduleId, request.cronExpression(), request.goalTemplate(), request.enabled()
        );
    }

    private Configuration configuration(AgentRequest request) {
        return new Configuration(
                request.name(),
                request.description(),
                request.sceneCode(),
                request.icon(),
                request.color(),
                request.tags(),
                new ProfileSpec(
                        request.profile().role(),
                        request.profile().goal(),
                        request.profile().personality(),
                        request.profile().style(),
                        request.profile().language(),
                        request.profile().constraints()
                ),
                new PlannerSpec(
                        request.planner().type(),
                        request.planner().config(),
                        request.planner().maxSteps(),
                        request.planner().maxDepth(),
                        request.planner().retryCount()
                ),
                request.tasks().stream().map(task -> new TaskSpec(
                        task.name(),
                        task.orderNo(),
                        task.type(),
                        task.referenceId(),
                        task.executionMode(),
                        task.condition(),
                        task.config(),
                        task.enabled()
                )).toList(),
                request.tools().stream().map(tool -> new ToolBinding(
                        tool.toolId(), tool.permission(), tool.approvalRequired()
                )).toList(),
                request.knowledge().stream().map(item -> new KnowledgeBinding(
                        item.knowledgeId(), item.required()
                )).toList(),
                request.memory() == null
                        ? null
                        : new MemoryPolicy(
                        request.memory().policy(),
                        request.memory().ownerTypes(),
                        request.memory().writeEnabled(),
                        request.memory().maxItems(),
                        request.memory().config()
                )
        );
    }

    public record AgentRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @Size(max = 100) String sceneCode,
            @Size(max = 40) String icon,
            @Size(max = 40) String color,
            @Size(max = 30) List<@NotBlank @Size(max = 100) String> tags,
            @NotNull @Valid ProfileRequest profile,
            @NotNull @Valid PlannerRequest planner,
            @Size(max = 100) @Valid List<TaskRequest> tasks,
            @Size(max = 100) @Valid List<ToolBindingRequest> tools,
            @Size(max = 100) @Valid List<KnowledgeBindingRequest> knowledge,
            @Valid MemoryRequest memory
    ) {
        public AgentRequest {
            tags = tags == null ? List.of() : List.copyOf(tags);
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            tools = tools == null ? List.of() : List.copyOf(tools);
            knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        }
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 1000) String role,
            @NotBlank @Size(max = 100000) String goal,
            @Size(max = 1000) String personality,
            @Size(max = 1000) String style,
            @Size(max = 40) String language,
            @Size(max = 100000) String constraints
    ) {
    }

    public record PlannerRequest(
            @Size(max = 40) String type,
            Map<String, Object> config,
            @Min(1) @Max(100) int maxSteps,
            @Min(1) @Max(20) int maxDepth,
            @Min(0) @Max(10) int retryCount
    ) {
    }

    public record TaskRequest(
            @NotBlank @Size(max = 200) String name,
            @Min(1) int orderNo,
            @NotBlank @Size(max = 40) String type,
            @Size(max = 100) String referenceId,
            @Size(max = 32) String executionMode,
            Map<String, Object> condition,
            Map<String, Object> config,
            boolean enabled
    ) {
    }

    public record ToolBindingRequest(
            @NotBlank @Size(max = 64) String toolId,
            @NotBlank @Size(max = 32) String permission,
            boolean approvalRequired
    ) {
    }

    public record KnowledgeBindingRequest(
            @NotBlank @Size(max = 64) String knowledgeId,
            boolean required
    ) {
    }

    public record MemoryRequest(
            @NotBlank @Size(max = 32) String policy,
            @Size(max = 10) List<@NotBlank @Size(max = 32) String> ownerTypes,
            boolean writeEnabled,
            @Min(0) @Max(1000) int maxItems,
            Map<String, Object> config
    ) {
    }

    public record StatusRequest(@NotBlank @Size(max = 32) String status) {
    }

    public record ExecuteRequest(
            @NotBlank @Size(max = 1000000) String goal,
            @Size(max = 64) String conversationId
    ) {
    }

    public record ApprovalRequest(
            boolean approved,
            @Size(max = 1000) String reason
    ) {
    }

    public record ScheduleRequest(
            @NotBlank @Size(max = 100) String cronExpression,
            @NotBlank @Size(max = 1000000) String goalTemplate,
            boolean enabled
    ) {
    }
}
