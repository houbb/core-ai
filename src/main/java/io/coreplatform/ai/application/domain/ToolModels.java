package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ToolModels {

    private ToolModels() {
    }

    public enum Status {
        DRAFT, TESTING, PUBLISHED, DEPRECATED, DISABLED, ARCHIVED
    }

    public record Tool(
            String id,
            String code,
            String name,
            String description,
            String category,
            String toolType,
            String icon,
            String ownerUser,
            Status status,
            int currentVersion,
            Integer publishedVersion,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Parameter(
            String id,
            String toolVersionId,
            String name,
            String type,
            boolean required,
            String defaultValue,
            String validationRule,
            String description,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Policy(
            String id,
            String toolId,
            String accessLevel,
            boolean readonly,
            boolean manualConfirm,
            boolean approvalRequired,
            int timeoutSeconds,
            int retryCount,
            boolean logContent,
            int retentionDays,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Version(
            String id,
            String toolId,
            int version,
            String schemaJson,
            String outputSchemaJson,
            String executorType,
            Map<String, Object> executorConfig,
            List<String> chain,
            String changeLog,
            boolean testsPassed,
            Instant lastTestedTime,
            Instant publishedTime,
            List<Parameter> parameters,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Version {
            executorConfig = executorConfig == null ? Map.of() : Map.copyOf(executorConfig);
            chain = chain == null ? List.of() : List.copyOf(chain);
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    public record View(Tool tool, Version currentVersion, Policy policy) {
    }

    public record TestCase(
            String id,
            String toolVersionId,
            String name,
            String inputJson,
            String expectedResult,
            boolean enabled,
            String lastActualResult,
            Boolean lastPassed,
            Instant lastRunTime,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record TestSuite(
            String toolId,
            int version,
            int total,
            int passed,
            boolean allPassed,
            List<TestCase> cases,
            Instant completedAt
    ) {
    }

    public record Execution(
            String id,
            String toolId,
            int toolVersion,
            Map<String, Object> request,
            Object response,
            String requestHash,
            String status,
            String mode,
            String approvalToken,
            String confirmedBy,
            String approvedBy,
            long latencyMs,
            String errorCode,
            String errorMessage,
            String traceId,
            Instant expireTime,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Execution {
            request = request == null ? Map.of() : Map.copyOf(request);
        }
    }

    public record MarketItem(
            String id,
            String name,
            String code,
            String publisher,
            String version,
            String category,
            String description,
            Map<String, Object> manifest,
            long installCount,
            boolean builtin
    ) {
    }

    public record ExecutorRequest(
            Tool tool,
            Version version,
            Map<String, Object> input,
            String traceId
    ) {
    }

    public record ExecutorResult(
            boolean executed,
            String mode,
            Object output,
            long latencyMs,
            Map<String, Object> metadata
    ) {
        public ExecutorResult {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
