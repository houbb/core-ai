package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.ToolModels.Execution;
import io.coreplatform.ai.application.domain.ToolModels.MarketItem;
import io.coreplatform.ai.application.domain.ToolModels.Status;
import io.coreplatform.ai.application.domain.ToolModels.TestCase;
import io.coreplatform.ai.application.domain.ToolModels.TestSuite;
import io.coreplatform.ai.application.domain.ToolModels.Tool;
import io.coreplatform.ai.application.domain.ToolModels.Version;
import io.coreplatform.ai.application.domain.ToolModels.View;
import io.coreplatform.ai.application.service.ToolService;
import io.coreplatform.ai.application.service.ToolService.Configuration;
import io.coreplatform.ai.application.service.ToolService.ParameterSpec;
import io.coreplatform.ai.application.service.ToolService.PolicySpec;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ToolController {

    private final ToolService service;

    public ToolController(ToolService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/ai/admin/tools")
    public List<Tool> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    @PostMapping("/api/v1/ai/admin/tools")
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody ToolRequest request) {
        return service.create(request.code(), configuration(request));
    }

    @GetMapping("/api/v1/ai/admin/tools/{id}")
    public View get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/ai/admin/tools/{id}")
    public View update(@PathVariable String id, @Valid @RequestBody ToolRequest request) {
        return service.update(id, configuration(request));
    }

    @PatchMapping("/api/v1/ai/admin/tools/{id}/status")
    public View status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return service.transition(id, request.status());
    }

    @GetMapping("/api/v1/ai/admin/tools/{id}/versions")
    public List<Version> versions(@PathVariable String id) {
        return service.versions(id);
    }

    @GetMapping("/api/v1/ai/admin/tools/{id}/test-cases")
    public List<TestCase> testCases(@PathVariable String id) {
        return service.testCases(id);
    }

    @PostMapping("/api/v1/ai/admin/tools/{id}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCase createTest(@PathVariable String id, @Valid @RequestBody TestRequest request) {
        return service.createTestCase(
                id, request.name(), request.inputJson(), request.expectedResult(), request.enabled()
        );
    }

    @PostMapping("/api/v1/ai/admin/tools/{id}/tests/run")
    public TestSuite runTests(@PathVariable String id) {
        return service.runTests(id);
    }

    @GetMapping("/api/v1/ai/admin/tools/{id}/executions")
    public List<Execution> executions(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.executions(id, limit);
    }

    @PostMapping("/api/v1/ai/tools/{reference}/execute")
    public Execution execute(
            @PathVariable String reference,
            @RequestBody(required = false) Map<String, Object> input
    ) {
        return service.executePublished(reference, input);
    }

    @PostMapping("/api/v1/ai/tools/executions/{id}/confirm")
    public Execution confirm(@PathVariable String id) {
        return service.confirm(id);
    }

    @PostMapping("/api/v1/ai/tools/executions/{id}/approve")
    public Execution approve(@PathVariable String id) {
        return service.approve(id);
    }

    @GetMapping("/api/v1/ai/admin/tool-market")
    public List<MarketItem> market() {
        return service.market();
    }

    @PostMapping("/api/v1/ai/admin/tool-market/{id}/install")
    @ResponseStatus(HttpStatus.CREATED)
    public View install(
            @PathVariable String id,
            @RequestBody(required = false) InstallRequest request
    ) {
        return service.install(id, request == null ? null : request.code());
    }

    @PostMapping("/api/v1/ai/admin/tools/import/openapi")
    @ResponseStatus(HttpStatus.CREATED)
    public View importOpenApi(@Valid @RequestBody OpenApiImportRequest request) {
        return service.importOpenApi(request.code(), request.document());
    }

    private Configuration configuration(ToolRequest request) {
        return new Configuration(
                request.name(),
                request.description(),
                request.category(),
                request.toolType(),
                request.icon(),
                request.schemaJson(),
                request.outputSchemaJson(),
                request.executorType(),
                request.executorConfig(),
                request.parameters().stream().map(parameter -> new ParameterSpec(
                        parameter.name(),
                        parameter.type(),
                        parameter.required(),
                        parameter.defaultValue(),
                        parameter.validationRule(),
                        parameter.description()
                )).toList(),
                request.chain(),
                request.changeLog(),
                request.policy() == null ? null : new PolicySpec(
                        request.policy().accessLevel(),
                        request.policy().readonly(),
                        request.policy().manualConfirm(),
                        request.policy().approvalRequired(),
                        request.policy().timeoutSeconds(),
                        request.policy().retryCount(),
                        request.policy().logContent(),
                        request.policy().retentionDays()
                )
        );
    }

    public record ToolRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @NotBlank @Size(max = 40) String toolType,
            @Size(max = 40) String icon,
            @Size(max = 200000) String schemaJson,
            @Size(max = 200000) String outputSchemaJson,
            @NotBlank @Size(max = 40) String executorType,
            Map<String, Object> executorConfig,
            @Valid List<ParameterRequest> parameters,
            @Size(max = 10) List<@NotBlank @Size(max = 100) String> chain,
            @Size(max = 1000) String changeLog,
            @Valid PolicyRequest policy
    ) {
        public ToolRequest {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            chain = chain == null ? List.of() : List.copyOf(chain);
        }
    }

    public record ParameterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 32) String type,
            boolean required,
            @Size(max = 100000) String defaultValue,
            @Size(max = 1000) String validationRule,
            @Size(max = 1000) String description
    ) {
    }

    public record PolicyRequest(
            @NotBlank @Size(max = 32) String accessLevel,
            boolean readonly,
            boolean manualConfirm,
            boolean approvalRequired,
            @Min(1) @Max(300) int timeoutSeconds,
            @Min(0) @Max(10) int retryCount,
            boolean logContent,
            @Min(1) @Max(3650) int retentionDays
    ) {
    }

    public record StatusRequest(@NotNull Status status) {
    }

    public record TestRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500000) String inputJson,
            @Size(max = 500000) String expectedResult,
            boolean enabled
    ) {
    }

    public record InstallRequest(@Size(max = 100) String code) {
    }

    public record OpenApiImportRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 1000000) String document
    ) {
    }
}
