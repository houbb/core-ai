package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.PromptRequests.AbAssignRequest;
import io.coreplatform.ai.api.request.PromptRequests.AbObservationRequest;
import io.coreplatform.ai.api.request.PromptRequests.CreateAbTestRequest;
import io.coreplatform.ai.api.request.PromptRequests.CreatePromptRequest;
import io.coreplatform.ai.api.request.PromptRequests.RenderRequest;
import io.coreplatform.ai.api.request.PromptRequests.StatusRequest;
import io.coreplatform.ai.api.request.PromptRequests.TestCaseRequest;
import io.coreplatform.ai.api.request.PromptRequests.UpdatePromptRequest;
import io.coreplatform.ai.api.request.PromptRequests.ValidateOutputRequest;
import io.coreplatform.ai.api.response.PromptResponses;
import io.coreplatform.ai.api.response.PromptResponses.AbAssignmentResponse;
import io.coreplatform.ai.api.response.PromptResponses.AbTestResponse;
import io.coreplatform.ai.api.response.PromptResponses.AuditResponse;
import io.coreplatform.ai.api.response.PromptResponses.DiffResponse;
import io.coreplatform.ai.api.response.PromptResponses.PromptResponse;
import io.coreplatform.ai.api.response.PromptResponses.PromptSummaryResponse;
import io.coreplatform.ai.api.response.PromptResponses.RenderLogResponse;
import io.coreplatform.ai.api.response.PromptResponses.RenderResponse;
import io.coreplatform.ai.api.response.PromptResponses.TestCaseResponse;
import io.coreplatform.ai.api.response.PromptResponses.TestSuiteResponse;
import io.coreplatform.ai.api.response.PromptResponses.VersionResponse;
import io.coreplatform.ai.application.domain.PromptSearchCriteria;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptVisibility;
import io.coreplatform.ai.application.service.PromptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/v1/ai/admin/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public List<PromptSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) PromptStatus status,
            @RequestParam(required = false) PromptVisibility visibility,
            @RequestParam(required = false) String sceneId
    ) {
        return promptService.search(new PromptSearchCriteria(
                query, category, status, visibility, sceneId
        )).stream().map(PromptResponses::summary).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptResponse create(@Valid @RequestBody CreatePromptRequest request) {
        return PromptResponses.from(promptService.create(
                request.code(),
                PromptRequestMapper.configuration(request)
        ));
    }

    @GetMapping("/{id}")
    public PromptResponse get(@PathVariable String id) {
        return PromptResponses.from(promptService.get(id));
    }

    @PutMapping("/{id}")
    public PromptResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdatePromptRequest request
    ) {
        return PromptResponses.from(promptService.update(
                id,
                PromptRequestMapper.configuration(request)
        ));
    }

    @PatchMapping("/{id}/status")
    public PromptResponse transition(
            @PathVariable String id,
            @Valid @RequestBody StatusRequest request
    ) {
        return PromptResponses.from(promptService.transition(id, request.status()));
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(@PathVariable String id) {
        return promptService.versions(id).stream().map(VersionResponse::from).toList();
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    public PromptResponse rollback(@PathVariable String id, @PathVariable int version) {
        return PromptResponses.from(promptService.rollback(id, version));
    }

    @GetMapping("/{id}/compare")
    public List<DiffResponse> compare(
            @PathVariable String id,
            @RequestParam int left,
            @RequestParam int right
    ) {
        return promptService.compare(id, left, right).stream().map(DiffResponse::from).toList();
    }

    @PostMapping("/{id}/render")
    public RenderResponse render(
            @PathVariable String id,
            @RequestBody(required = false) RenderRequest request
    ) {
        return RenderResponse.from(promptService.renderCurrent(
                id,
                request == null ? null : request.variables()
        ));
    }

    @PostMapping("/{id}/validate-output")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateOutput(
            @PathVariable String id,
            @Valid @RequestBody ValidateOutputRequest request
    ) {
        promptService.validateOutput(id, request.version(), request.output());
    }

    @GetMapping("/{id}/test-cases")
    public List<TestCaseResponse> testCases(@PathVariable String id) {
        return promptService.testCases(id).stream().map(TestCaseResponse::from).toList();
    }

    @PostMapping("/{id}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse createTestCase(
            @PathVariable String id,
            @Valid @RequestBody TestCaseRequest request
    ) {
        return TestCaseResponse.from(promptService.createTestCase(
                id,
                request.name(),
                request.inputJson(),
                request.expectedOutput(),
                request.enabled()
        ));
    }

    @PutMapping("/{id}/test-cases/{testCaseId}")
    public TestCaseResponse updateTestCase(
            @PathVariable String id,
            @PathVariable String testCaseId,
            @Valid @RequestBody TestCaseRequest request
    ) {
        return TestCaseResponse.from(promptService.updateTestCase(
                id,
                testCaseId,
                request.name(),
                request.inputJson(),
                request.expectedOutput(),
                request.enabled()
        ));
    }

    @DeleteMapping("/{id}/test-cases/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTestCase(@PathVariable String id, @PathVariable String testCaseId) {
        promptService.deleteTestCase(id, testCaseId);
    }

    @PostMapping("/{id}/tests/run")
    public TestSuiteResponse runTests(@PathVariable String id) {
        return TestSuiteResponse.from(promptService.runTests(id));
    }

    @GetMapping("/{id}/ab-tests")
    public List<AbTestResponse> abTests(@PathVariable String id) {
        return promptService.abTests(id).stream().map(AbTestResponse::from).toList();
    }

    @PostMapping("/{id}/ab-tests")
    @ResponseStatus(HttpStatus.CREATED)
    public AbTestResponse createAbTest(
            @PathVariable String id,
            @Valid @RequestBody CreateAbTestRequest request
    ) {
        return AbTestResponse.from(promptService.createAbTest(
                id,
                request.name(),
                request.sceneId(),
                request.versionA(),
                request.versionB(),
                request.trafficRatio()
        ));
    }

    @PostMapping("/{id}/ab-tests/{abTestId}/assign")
    public AbAssignmentResponse assign(
            @PathVariable String id,
            @PathVariable String abTestId,
            @Valid @RequestBody AbAssignRequest request
    ) {
        return AbAssignmentResponse.from(promptService.assignAbTest(
                id, abTestId, request.subjectKey()
        ));
    }

    @PostMapping("/{id}/ab-tests/{abTestId}/observations")
    public AbTestResponse observe(
            @PathVariable String id,
            @PathVariable String abTestId,
            @Valid @RequestBody AbObservationRequest request
    ) {
        return AbTestResponse.from(promptService.recordAbObservation(
                id,
                abTestId,
                request.variant(),
                request.success(),
                request.latencyMs(),
                request.cost(),
                request.score()
        ));
    }

    @GetMapping("/{id}/audit")
    public List<AuditResponse> audit(@PathVariable String id) {
        return promptService.audit(id).stream().map(AuditResponse::from).toList();
    }

    @GetMapping("/{id}/render-logs")
    public List<RenderLogResponse> renderLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return promptService.renderLogs(id, limit).stream().map(RenderLogResponse::from).toList();
    }
}
