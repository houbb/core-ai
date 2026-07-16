package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AgentModels {

    private AgentModels() {
    }

    public record Agent(
            String id,
            String code,
            String name,
            String description,
            String status,
            String ownerUser,
            String sceneCode,
            String icon,
            String color,
            List<String> tags,
            int currentVersion,
            Integer publishedVersion,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Agent {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record Profile(
            String id,
            String agentId,
            String role,
            String goal,
            String personality,
            String style,
            String language,
            String constraints
    ) {
    }

    public record Planner(
            String id,
            String agentId,
            String plannerType,
            Map<String, Object> config,
            int maxSteps,
            int maxDepth,
            int retryCount
    ) {
        public Planner {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record Task(
            String id,
            String agentId,
            String name,
            int orderNo,
            String taskType,
            String referenceId,
            String executionMode,
            Map<String, Object> condition,
            Map<String, Object> config,
            boolean enabled
    ) {
        public Task {
            condition = condition == null ? Map.of() : Map.copyOf(condition);
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record ToolBinding(
            String toolId,
            String permission,
            boolean approvalRequired
    ) {
    }

    public record KnowledgeBinding(String knowledgeId, boolean required) {
    }

    public record MemoryPolicy(
            String policy,
            List<String> ownerTypes,
            boolean writeEnabled,
            int maxItems,
            Map<String, Object> config
    ) {
        public MemoryPolicy {
            ownerTypes = ownerTypes == null ? List.of() : List.copyOf(ownerTypes);
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record Definition(
            Agent agent,
            Profile profile,
            Planner planner,
            List<Task> tasks,
            List<ToolBinding> tools,
            List<KnowledgeBinding> knowledge,
            MemoryPolicy memory
    ) {
        public Definition {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            tools = tools == null ? List.of() : List.copyOf(tools);
            knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
        }
    }

    public record Execution(
            String id,
            String agentId,
            int agentVersion,
            String conversationId,
            String goal,
            String status,
            int currentTaskNo,
            String result,
            String errorCode,
            String traceId,
            Instant startedAt,
            Instant endedAt,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record TraceStep(
            String id,
            String executionId,
            int stepNo,
            String stage,
            String action,
            String result,
            String status,
            long latencyMs,
            Map<String, Object> metadata,
            Instant createTime
    ) {
        public TraceStep {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Approval(
            String id,
            String executionId,
            String taskId,
            String approvalType,
            String requestDetail,
            String status,
            String approvalToken,
            String approvedBy,
            Instant approvedAt,
            String rejectionReason,
            Instant createTime,
            Instant updateTime
    ) {
    }

    public record Schedule(
            String id,
            String agentId,
            String cronExpression,
            String goalTemplate,
            boolean enabled,
            Instant nextRunTime,
            Instant lastRunTime,
            Instant createTime,
            Instant updateTime
    ) {
    }

    public record ExecutionView(
            Execution execution,
            List<TraceStep> trace,
            Approval approval
    ) {
    }
}
