package io.coreplatform.ai.api.response;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.PromptAbAssignment;
import io.coreplatform.ai.application.domain.PromptAbTest;
import io.coreplatform.ai.application.domain.PromptChainStep;
import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.domain.PromptDiffLine;
import io.coreplatform.ai.application.domain.PromptEvaluationResult;
import io.coreplatform.ai.application.domain.PromptGuardrail;
import io.coreplatform.ai.application.domain.PromptOutputSchema;
import io.coreplatform.ai.application.domain.PromptRenderLog;
import io.coreplatform.ai.application.domain.PromptRenderedStage;
import io.coreplatform.ai.application.domain.PromptRenderResult;
import io.coreplatform.ai.application.domain.PromptTestCase;
import io.coreplatform.ai.application.domain.PromptTestSuiteResult;
import io.coreplatform.ai.application.domain.PromptVariable;
import io.coreplatform.ai.application.domain.PromptVersionData;
import io.coreplatform.ai.application.domain.PromptView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PromptResponses {

    private PromptResponses() {
    }

    public static PromptSummaryResponse summary(PromptData prompt) {
        return new PromptSummaryResponse(
                prompt.id(),
                prompt.code(),
                prompt.name(),
                prompt.description(),
                prompt.category(),
                prompt.sceneId(),
                prompt.status().name(),
                prompt.currentVersion(),
                prompt.publishedVersion(),
                prompt.visibility().name(),
                prompt.projectCode(),
                prompt.departmentCode(),
                prompt.ownerUser(),
                prompt.updateTime()
        );
    }

    public static PromptResponse from(PromptView view) {
        return new PromptResponse(
                summary(view.prompt()),
                VersionResponse.from(view.currentVersion()),
                view.publishedVersion() == null ? null : VersionResponse.from(view.publishedVersion())
        );
    }

    public record PromptSummaryResponse(
            String id,
            String code,
            String name,
            String description,
            String category,
            String sceneId,
            String status,
            int currentVersion,
            Integer publishedVersion,
            String visibility,
            String projectCode,
            String departmentCode,
            String ownerUser,
            Instant updateTime
    ) {
    }

    public record PromptResponse(
            PromptSummaryResponse prompt,
            VersionResponse currentVersion,
            VersionResponse publishedVersion
    ) {
    }

    public record VersionResponse(
            String id,
            int version,
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            String changeLog,
            List<VariableResponse> variables,
            SchemaResponse outputSchema,
            List<GuardrailResponse> guardrails,
            List<PromptChainStep> chain,
            boolean testsPassed,
            Instant lastTestedTime,
            Instant publishedTime,
            Instant createTime,
            String createUser
    ) {

        public static VersionResponse from(PromptVersionData version) {
            return new VersionResponse(
                    version.id(),
                    version.version(),
                    version.systemPrompt(),
                    version.userPrompt(),
                    version.assistantPrompt(),
                    version.changeLog(),
                    version.variables().stream().map(VariableResponse::from).toList(),
                    SchemaResponse.from(version.outputSchema()),
                    version.guardrails().stream().map(GuardrailResponse::from).toList(),
                    version.chain(),
                    version.testsPassed(),
                    version.lastTestedTime(),
                    version.publishedTime(),
                    version.createTime(),
                    version.createUser()
            );
        }
    }

    public record VariableResponse(
            String name,
            String type,
            boolean required,
            String defaultValue,
            String description
    ) {

        static VariableResponse from(PromptVariable variable) {
            return new VariableResponse(
                    variable.name(),
                    variable.type().name(),
                    variable.required(),
                    variable.defaultValue(),
                    variable.description()
            );
        }
    }

    public record SchemaResponse(String schemaJson, boolean strictMode) {

        static SchemaResponse from(PromptOutputSchema schema) {
            return new SchemaResponse(schema.schemaJson(), schema.strictMode());
        }
    }

    public record GuardrailResponse(
            String type,
            String phase,
            String configJson,
            boolean enabled
    ) {

        static GuardrailResponse from(PromptGuardrail guardrail) {
            return new GuardrailResponse(
                    guardrail.type().name(),
                    guardrail.phase().name(),
                    guardrail.configJson(),
                    guardrail.enabled()
            );
        }
    }

    public record RenderResponse(
            String promptId,
            String promptCode,
            int version,
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            List<StageResponse> chain,
            int characterCount,
            int estimatedTokens,
            String outputSchema,
            boolean strictSchema,
            String mode
    ) {

        public static RenderResponse from(PromptRenderResult result) {
            return new RenderResponse(
                    result.promptId(),
                    result.promptCode(),
                    result.version(),
                    result.systemPrompt(),
                    result.userPrompt(),
                    result.assistantPrompt(),
                    result.chain().stream().map(StageResponse::from).toList(),
                    result.characterCount(),
                    result.estimatedTokens(),
                    result.outputSchema(),
                    result.strictSchema(),
                    result.mode()
            );
        }
    }

    public record StageResponse(
            String promptId,
            String promptCode,
            int version,
            String systemPrompt,
            String userPrompt,
            String assistantPrompt,
            int estimatedTokens
    ) {

        static StageResponse from(PromptRenderedStage stage) {
            return new StageResponse(
                    stage.promptId(),
                    stage.promptCode(),
                    stage.version(),
                    stage.systemPrompt(),
                    stage.userPrompt(),
                    stage.assistantPrompt(),
                    stage.estimatedTokens()
            );
        }
    }

    public record TestCaseResponse(
            String id,
            String name,
            String inputJson,
            String expectedOutput,
            boolean enabled,
            String lastActualOutput,
            Boolean lastPassed,
            Instant lastRunTime
    ) {

        public static TestCaseResponse from(PromptTestCase testCase) {
            return new TestCaseResponse(
                    testCase.id(),
                    testCase.name(),
                    testCase.inputJson(),
                    testCase.expectedOutput(),
                    testCase.enabled(),
                    testCase.lastActualOutput(),
                    testCase.lastPassed(),
                    testCase.lastRunTime()
            );
        }
    }

    public record EvaluationResponse(
            String testCaseId,
            String testCaseName,
            boolean passed,
            String expectedOutput,
            String actualOutput,
            String mode,
            boolean executed,
            RenderResponse render
    ) {

        static EvaluationResponse from(PromptEvaluationResult result) {
            return new EvaluationResponse(
                    result.testCaseId(),
                    result.testCaseName(),
                    result.passed(),
                    result.expectedOutput(),
                    result.actualOutput(),
                    result.mode(),
                    result.executed(),
                    RenderResponse.from(result.render())
            );
        }
    }

    public record TestSuiteResponse(
            String promptId,
            int version,
            boolean passed,
            String mode,
            boolean executed,
            List<EvaluationResponse> results
    ) {

        public static TestSuiteResponse from(PromptTestSuiteResult result) {
            return new TestSuiteResponse(
                    result.promptId(),
                    result.version(),
                    result.passed(),
                    result.mode(),
                    result.executed(),
                    result.results().stream().map(EvaluationResponse::from).toList()
            );
        }
    }

    public record DiffResponse(
            String section,
            String type,
            int leftLine,
            int rightLine,
            String text
    ) {

        public static DiffResponse from(PromptDiffLine line) {
            return new DiffResponse(
                    line.section(),
                    line.type(),
                    line.leftLine(),
                    line.rightLine(),
                    line.text()
            );
        }
    }

    public record AbTestResponse(
            String id,
            String promptId,
            String sceneId,
            String name,
            int versionA,
            int versionB,
            int trafficRatio,
            boolean enabled,
            long sampleA,
            long sampleB,
            long successA,
            long successB,
            double averageLatencyA,
            double averageLatencyB,
            BigDecimal costATotal,
            BigDecimal costBTotal,
            double averageScoreA,
            double averageScoreB
    ) {

        public static AbTestResponse from(PromptAbTest test) {
            return new AbTestResponse(
                    test.id(),
                    test.promptId(),
                    test.sceneId(),
                    test.name(),
                    test.versionA(),
                    test.versionB(),
                    test.trafficRatio(),
                    test.enabled(),
                    test.sampleA(),
                    test.sampleB(),
                    test.successA(),
                    test.successB(),
                    average(test.latencyATotal(), test.sampleA()),
                    average(test.latencyBTotal(), test.sampleB()),
                    test.costATotal(),
                    test.costBTotal(),
                    average(test.scoreATotal(), test.sampleA()),
                    average(test.scoreBTotal(), test.sampleB())
            );
        }

        private static double average(double total, long count) {
            return count == 0 ? 0 : total / count;
        }
    }

    public record AbAssignmentResponse(
            String abTestId,
            String variant,
            int version,
            int bucket
    ) {

        public static AbAssignmentResponse from(PromptAbAssignment assignment) {
            return new AbAssignmentResponse(
                    assignment.abTestId(),
                    assignment.variant(),
                    assignment.version(),
                    assignment.bucket()
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

    public record RenderLogResponse(
            String id,
            String promptVersionId,
            String variableNames,
            boolean contentStored,
            String contentHash,
            int estimatedTokens,
            String mode,
            Instant expireTime,
            Instant createTime,
            String createUser
    ) {

        public static RenderLogResponse from(PromptRenderLog log) {
            return new RenderLogResponse(
                    log.id(),
                    log.promptVersionId(),
                    log.variableNames(),
                    log.renderedPrompt() != null,
                    log.contentHash(),
                    log.estimatedTokens(),
                    log.mode(),
                    log.expireTime(),
                    log.createTime(),
                    log.createUser()
            );
        }
    }
}
