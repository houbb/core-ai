package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.SceneRequests.ExecuteRequest;
import io.coreplatform.ai.api.response.SceneResponses;
import io.coreplatform.ai.api.response.SceneResponses.ExecutionResponse;
import io.coreplatform.ai.api.response.SceneResponses.SceneResponse;
import io.coreplatform.ai.application.service.SceneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/scenes")
public class SceneRuntimeController {

    private final SceneService sceneService;

    public SceneRuntimeController(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @GetMapping
    public List<SceneResponse> catalog() {
        return sceneService.catalog().stream().map(SceneResponses::from).toList();
    }

    @GetMapping("/{code}")
    public SceneResponse get(@PathVariable String code) {
        return SceneResponses.from(sceneService.runtimeScene(code));
    }

    @PostMapping("/{code}/execute")
    public ExecutionResponse execute(
            @PathVariable String code,
            @Valid @RequestBody ExecuteRequest request
    ) {
        return ExecutionResponse.from(sceneService.execute(
                code,
                request.input(),
                request.variables()
        ));
    }
}
