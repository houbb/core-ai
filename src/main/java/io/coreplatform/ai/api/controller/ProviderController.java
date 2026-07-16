package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.api.request.ProviderRequests.CreateProviderRequest;
import io.coreplatform.ai.api.request.ProviderRequests.SetEnabledRequest;
import io.coreplatform.ai.api.request.ProviderRequests.UpdateProviderRequest;
import io.coreplatform.ai.api.response.ProviderResponses;
import io.coreplatform.ai.api.response.ProviderResponses.AuditResponse;
import io.coreplatform.ai.api.response.ProviderResponses.ConnectionResponse;
import io.coreplatform.ai.api.response.ProviderResponses.ModelResponse;
import io.coreplatform.ai.api.response.ProviderResponses.PresetResponse;
import io.coreplatform.ai.api.response.ProviderResponses.ProviderResponse;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.service.ProviderPresetService;
import io.coreplatform.ai.application.service.ProviderService;
import io.coreplatform.ai.application.service.ProviderService.CreateProviderCommand;
import io.coreplatform.ai.application.service.ProviderService.UpdateProviderCommand;
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
@RequestMapping("/api/v1/ai/admin/providers")
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderPresetService presetService;

    public ProviderController(ProviderService providerService, ProviderPresetService presetService) {
        this.providerService = providerService;
        this.presetService = presetService;
    }

    @GetMapping
    public List<ProviderResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Capability capability,
            @RequestParam(required = false) String tag
    ) {
        return providerService.search(new ProviderSearchCriteria(query, enabled, location, capability, tag))
                .stream()
                .map(provider -> ProviderResponses.from(provider, null))
                .toList();
    }

    @GetMapping("/presets")
    public List<PresetResponse> presets() {
        return presetService.findAll().stream().map(ProviderResponses::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse create(@Valid @RequestBody CreateProviderRequest request) {
        return ProviderResponses.from(providerService.create(new CreateProviderCommand(
                request.code(),
                request.name(),
                request.description(),
                request.type(),
                request.endpoint(),
                request.priority(),
                request.weight(),
                request.timeoutSeconds(),
                request.retryCount(),
                request.apiKey(),
                request.organization(),
                request.proxy(),
                request.tlsVerify(),
                request.headers(),
                request.customParameters(),
                request.tags()
        )), List.of());
    }

    @GetMapping("/{providerId}")
    public ProviderResponse get(@PathVariable String providerId) {
        return ProviderResponses.from(
                providerService.get(providerId),
                providerService.models(providerId)
        );
    }

    @PutMapping("/{providerId}")
    public ProviderResponse update(
            @PathVariable String providerId,
            @Valid @RequestBody UpdateProviderRequest request
    ) {
        return ProviderResponses.from(providerService.update(providerId, new UpdateProviderCommand(
                request.code(),
                request.name(),
                request.description(),
                request.type(),
                request.endpoint(),
                request.priority(),
                request.weight(),
                request.timeoutSeconds(),
                request.retryCount(),
                request.apiKey(),
                request.organization(),
                request.proxy(),
                request.tlsVerify(),
                request.headers(),
                request.customParameters(),
                request.tags()
        )), providerService.models(providerId));
    }

    @PatchMapping("/{providerId}/enabled")
    public ProviderResponse setEnabled(
            @PathVariable String providerId,
            @RequestBody SetEnabledRequest request
    ) {
        return ProviderResponses.from(
                providerService.setEnabled(providerId, request.enabled()),
                providerService.models(providerId)
        );
    }

    @PostMapping("/{providerId}/test")
    public ConnectionResponse testConnection(@PathVariable String providerId) {
        return ConnectionResponse.from(providerService.testConnection(providerId));
    }

    @PostMapping("/{providerId}/models/refresh")
    public ConnectionResponse refreshModels(@PathVariable String providerId) {
        return ConnectionResponse.from(providerService.refreshModels(providerId));
    }

    @GetMapping("/{providerId}/models")
    public List<ModelResponse> models(@PathVariable String providerId) {
        return providerService.models(providerId).stream().map(ModelResponse::from).toList();
    }

    @GetMapping("/{providerId}/audit")
    public List<AuditResponse> audit(@PathVariable String providerId) {
        return providerService.audit(providerId).stream().map(ProviderResponses::from).toList();
    }

    @DeleteMapping("/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String providerId) {
        providerService.delete(providerId);
    }
}
