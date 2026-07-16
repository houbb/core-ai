package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.PromptRequests.RenderRequest;
import io.coreplatform.ai.api.request.PromptRequests.ValidateOutputRequest;
import io.coreplatform.ai.api.response.PromptResponses;
import io.coreplatform.ai.api.response.PromptResponses.PromptSummaryResponse;
import io.coreplatform.ai.api.response.PromptResponses.RenderResponse;
import io.coreplatform.ai.application.service.PromptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/prompts")
public class PromptRuntimeController {

    private final PromptService promptService;

    public PromptRuntimeController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public List<PromptSummaryResponse> catalog() {
        return promptService.publishedCatalog().stream().map(PromptResponses::summary).toList();
    }

    @PostMapping("/{code}/render")
    public RenderResponse render(
            @PathVariable String code,
            @RequestParam(required = false) Integer version,
            @RequestBody(required = false) RenderRequest request
    ) {
        return RenderResponse.from(promptService.renderRuntime(
                code,
                version,
                request == null ? null : request.variables()
        ));
    }

    @PostMapping("/{code}/validate-output")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateOutput(
            @PathVariable String code,
            @Valid @RequestBody ValidateOutputRequest request
    ) {
        promptService.validatePublishedOutput(code, request.version(), request.output());
    }
}
