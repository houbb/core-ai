package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.ModelRequests.AliasRequest;
import io.coreplatform.ai.api.response.ModelResponses;
import io.coreplatform.ai.api.response.ModelResponses.AliasResponse;
import io.coreplatform.ai.api.response.ModelResponses.ModelResponse;
import io.coreplatform.ai.application.service.ModelService;
import io.coreplatform.ai.application.service.ModelService.AliasCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/ai/admin/model-aliases")
public class ModelAliasController {

    private final ModelService modelService;

    public ModelAliasController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public List<AliasResponse> aliases(@RequestParam(required = false) String alias) {
        return modelService.aliases(alias).stream().map(AliasResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AliasResponse create(@Valid @RequestBody AliasRequest request) {
        return AliasResponse.from(modelService.saveAlias(null, command(request)));
    }

    @PutMapping("/{id}")
    public AliasResponse update(@PathVariable String id, @Valid @RequestBody AliasRequest request) {
        return AliasResponse.from(modelService.saveAlias(id, command(request)));
    }

    @GetMapping("/{alias}/resolve")
    public List<ModelResponse> resolve(@PathVariable String alias) {
        return modelService.resolveAlias(alias).stream().map(ModelResponses::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        modelService.deleteAlias(id);
    }

    private AliasCommand command(AliasRequest request) {
        return new AliasCommand(
                request.alias(),
                request.modelId(),
                request.scene(),
                request.priority(),
                request.enabled()
        );
    }
}
