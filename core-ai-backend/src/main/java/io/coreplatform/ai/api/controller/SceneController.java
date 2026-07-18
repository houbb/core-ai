package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.SceneRequests.CreateSceneRequest;
import io.coreplatform.ai.api.request.SceneRequests.ExecuteRequest;
import io.coreplatform.ai.api.request.SceneRequests.ImportSceneRequest;
import io.coreplatform.ai.api.request.SceneRequests.SaveTemplateRequest;
import io.coreplatform.ai.api.request.SceneRequests.StatusRequest;
import io.coreplatform.ai.api.request.SceneRequests.UpdateSceneRequest;
import io.coreplatform.ai.api.response.SceneResponses;
import io.coreplatform.ai.api.response.SceneResponses.AuditResponse;
import io.coreplatform.ai.api.response.SceneResponses.ExecutionResponse;
import io.coreplatform.ai.api.response.SceneResponses.PackageResponse;
import io.coreplatform.ai.api.response.SceneResponses.SceneResponse;
import io.coreplatform.ai.api.response.SceneResponses.TemplateResponse;
import io.coreplatform.ai.api.response.SceneResponses.VersionResponse;
import io.coreplatform.ai.application.domain.ScenePackage;
import io.coreplatform.ai.application.domain.SceneSearchCriteria;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.service.SceneService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/ai/admin/scenes")
public class SceneController {

    private final SceneService sceneService;

    public SceneController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping
    public List<SceneResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SceneStatus status,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean recommended
    ) {
        return sceneService.search(new SceneSearchCriteria(
                query,
                category,
                status,
                enabled,
                recommended
        )).stream().map(SceneResponses::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SceneResponse create(@Valid @RequestBody CreateSceneRequest request) {
        return SceneResponses.from(sceneService.create(request.code(), configuration(request)));
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public SceneResponse importScene(@Valid @RequestBody ImportSceneRequest request) {
        return SceneResponses.from(sceneService.importScene(new ScenePackage(
                request.formatVersion(),
                request.code(),
                request.version(),
                configuration(request.configuration())
        )));
    }

    @GetMapping("/{id}")
    public SceneResponse get(@PathVariable String id) {
        return SceneResponses.from(sceneService.get(id));
    }

    @PutMapping("/{id}")
    public SceneResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateSceneRequest request
    ) {
        return SceneResponses.from(sceneService.update(id, configuration(request)));
    }

    @PatchMapping("/{id}/status")
    public SceneResponse transition(
            @PathVariable String id,
            @Valid @RequestBody StatusRequest request
    ) {
        return SceneResponses.from(sceneService.transition(id, request.status()));
    }

    @PostMapping("/{id}/test")
    public ExecutionResponse test(
            @PathVariable String id,
            @Valid @RequestBody ExecuteRequest request
    ) {
        return ExecutionResponse.from(sceneService.test(id, request.input(), request.variables()));
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(@PathVariable String id) {
        return sceneService.versions(id).stream().map(VersionResponse::from).toList();
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    public SceneResponse rollback(@PathVariable String id, @PathVariable int version) {
        return SceneResponses.from(sceneService.rollback(id, version));
    }

    @GetMapping("/{id}/export")
    public PackageResponse exportScene(@PathVariable String id) {
        return PackageResponse.from(sceneService.exportScene(id));
    }

    @PostMapping("/{id}/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse saveTemplate(
            @PathVariable String id,
            @Valid @RequestBody SaveTemplateRequest request
    ) {
        return TemplateResponse.from(sceneService.saveAsTemplate(
                id,
                request.templateName(),
                request.defaultCode()
        ));
    }

    @GetMapping("/{id}/audit")
    public List<AuditResponse> audit(@PathVariable String id) {
        return sceneService.audit(id).stream().map(AuditResponse::from).toList();
    }

    private io.coreplatform.ai.application.domain.SceneConfiguration configuration(
            CreateSceneRequest request
    ) {
        return SceneRequestMapper.configuration(
                request.name(),
                request.description(),
                request.category(),
                request.icon(),
                request.recommended(),
                request.models(),
                request.parameters(),
                request.prompt(),
                request.permissions(),
                request.workflow()
        );
    }

    private io.coreplatform.ai.application.domain.SceneConfiguration configuration(
            UpdateSceneRequest request
    ) {
        return SceneRequestMapper.configuration(
                request.name(),
                request.description(),
                request.category(),
                request.icon(),
                request.recommended(),
                request.models(),
                request.parameters(),
                request.prompt(),
                request.permissions(),
                request.workflow()
        );
    }
}
