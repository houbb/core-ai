package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.ModelRequests.AddPricingRequest;
import io.coreplatform.ai.api.request.ModelRequests.CapabilityOverrideRequest;
import io.coreplatform.ai.api.request.ModelRequests.CompareRequest;
import io.coreplatform.ai.api.request.ModelRequests.FlagsRequest;
import io.coreplatform.ai.api.request.ModelRequests.StatusRequest;
import io.coreplatform.ai.api.request.ModelRequests.UpdateModelRequest;
import io.coreplatform.ai.api.request.ModelRequests.UpdateParametersRequest;
import io.coreplatform.ai.api.response.ModelResponses;
import io.coreplatform.ai.api.response.ModelResponses.AuditResponse;
import io.coreplatform.ai.api.response.ModelResponses.DefaultResponse;
import io.coreplatform.ai.api.response.ModelResponses.ModelResponse;
import io.coreplatform.ai.api.response.ModelResponses.PricingResponse;
import io.coreplatform.ai.api.response.ModelResponses.RecommendationResponse;
import io.coreplatform.ai.api.response.ModelResponses.SyncResponse;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelSearchCriteria;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.service.ModelService;
import io.coreplatform.ai.application.service.ModelService.PricingCommand;
import io.coreplatform.ai.application.service.ModelService.RecommendationMode;
import io.coreplatform.ai.application.service.ModelService.UpdateModelCommand;
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
@RequestMapping("/api/v1/ai/admin/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public List<ModelResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) ModelCategory category,
            @RequestParam(required = false) Capability capability,
            @RequestParam(required = false) ModelStatus status,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean recommended,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Integer minimumContextTokens,
            @RequestParam(required = false) String tag
    ) {
        return modelService.search(new ModelSearchCriteria(
                query,
                providerId,
                category,
                capability,
                status,
                enabled,
                favorite,
                recommended,
                available,
                minimumContextTokens,
                tag
        )).stream().map(ModelResponses::from).toList();
    }

    @PostMapping("/sync")
    public SyncResponse synchronize(@RequestParam(required = false) String providerId) {
        return new SyncResponse(modelService.synchronize(providerId));
    }

    @PostMapping("/compare")
    public List<ModelResponse> compare(@Valid @RequestBody CompareRequest request) {
        return modelService.compare(request.ids()).stream().map(ModelResponses::from).toList();
    }

    @GetMapping("/recommend")
    public List<RecommendationResponse> recommend(
            @RequestParam Capability capability,
            @RequestParam(defaultValue = "BEST") RecommendationMode mode,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return modelService.recommend(capability, mode, limit)
                .stream()
                .map(RecommendationResponse::from)
                .toList();
    }

    @GetMapping("/defaults")
    public List<DefaultResponse> defaults() {
        return modelService.defaults().stream().map(DefaultResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ModelResponse get(@PathVariable String id) {
        return ModelResponses.from(modelService.get(id));
    }

    @PutMapping("/{id}")
    public ModelResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateModelRequest request
    ) {
        return ModelResponses.from(modelService.update(id, new UpdateModelCommand(
                request.displayName(),
                request.category(),
                request.description(),
                request.maxContextTokens(),
                request.maxInputTokens(),
                request.maxOutputTokens(),
                request.defaultMaxTokens(),
                request.contextManuallyOverridden(),
                request.tags()
        )));
    }

    @PatchMapping("/{id}/status")
    public ModelResponse transition(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return ModelResponses.from(modelService.transition(id, request.status()));
    }

    @PutMapping("/{id}/capabilities")
    public ModelResponse updateCapabilities(
            @PathVariable String id,
            @Valid @RequestBody CapabilityOverrideRequest request
    ) {
        return ModelResponses.from(modelService.updateCapabilities(id, request.overrides()));
    }

    @PostMapping("/{id}/capabilities/reset")
    public ModelResponse resetCapabilities(@PathVariable String id) {
        return ModelResponses.from(modelService.resetCapabilities(id));
    }

    @PutMapping("/{id}/parameters")
    public ModelResponse updateParameters(
            @PathVariable String id,
            @Valid @RequestBody UpdateParametersRequest request
    ) {
        return ModelResponses.from(modelService.updateParameters(id, new ModelParameters(
                request.temperature(),
                request.topP(),
                request.frequencyPenalty(),
                request.presencePenalty(),
                request.maxOutputTokens(),
                request.reasoningEffort(),
                request.seed()
        )));
    }

    @PostMapping("/{id}/pricing")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingResponse addPricing(
            @PathVariable String id,
            @Valid @RequestBody AddPricingRequest request
    ) {
        return PricingResponse.from(modelService.addPricing(id, new PricingCommand(
                request.currency(),
                request.promptPrice(),
                request.completionPrice(),
                request.cacheReadPrice(),
                request.cacheWritePrice(),
                request.effectiveTime(),
                request.notes()
        )));
    }

    @PatchMapping("/{id}/flags")
    public ModelResponse setFlags(@PathVariable String id, @RequestBody FlagsRequest request) {
        return ModelResponses.from(
                modelService.setFlags(id, request.favorite(), request.recommended())
        );
    }

    @GetMapping("/{id}/audit")
    public List<AuditResponse> audit(@PathVariable String id) {
        return modelService.audit(id).stream().map(AuditResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        modelService.delete(id);
    }
}
