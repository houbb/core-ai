package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.SceneRequests.InstantiateTemplateRequest;
import io.coreplatform.ai.api.response.SceneResponses;
import io.coreplatform.ai.api.response.SceneResponses.SceneResponse;
import io.coreplatform.ai.api.response.SceneResponses.TemplateResponse;
import io.coreplatform.ai.application.service.SceneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/admin/scene-templates")
public class SceneTemplateController {

    private final SceneService sceneService;

    public SceneTemplateController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping
    public List<TemplateResponse> templates() {
        return sceneService.templates().stream().map(TemplateResponse::from).toList();
    }

    @PostMapping("/{id}/instantiate")
    @ResponseStatus(HttpStatus.CREATED)
    public SceneResponse instantiate(
            @PathVariable String id,
            @Valid @RequestBody InstantiateTemplateRequest request
    ) {
        return SceneResponses.from(sceneService.instantiateTemplate(
                id,
                request.code(),
                request.name()
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        sceneService.deleteTemplate(id);
    }
}
