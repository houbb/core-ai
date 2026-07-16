package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.GatewayModels.Dashboard;
import io.coreplatform.ai.application.domain.GatewayModels.Gateway;
import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.GatewayModels.Policy;
import io.coreplatform.ai.application.domain.GatewayModels.Route;
import io.coreplatform.ai.application.domain.GatewayModels.Trace;
import io.coreplatform.ai.application.service.GatewayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
import java.util.Map;

@RestController
public class GatewayController {

    private final GatewayService service;

    public GatewayController(GatewayService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/ai/admin/gateways")
    public List<Gateway> gateways() {
        return service.gateways();
    }

    @GetMapping("/api/v1/ai/admin/gateways/{id}/routes")
    public List<Route> routes(@PathVariable String id) {
        return service.routes(id);
    }

    @PostMapping("/api/v1/ai/admin/gateways/{id}/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public Route createRoute(@PathVariable String id, @Valid @RequestBody RouteRequest request) {
        return saveRoute(null, id, request);
    }

    @PutMapping("/api/v1/ai/admin/gateways/{gatewayId}/routes/{routeId}")
    public Route updateRoute(
            @PathVariable String gatewayId,
            @PathVariable String routeId,
            @Valid @RequestBody RouteRequest request
    ) {
        return saveRoute(routeId, gatewayId, request);
    }

    @GetMapping("/api/v1/ai/admin/gateways/{id}/policy")
    public Policy policy(@PathVariable String id) {
        return service.policy(id);
    }

    @PutMapping("/api/v1/ai/admin/gateways/{id}/policy")
    public Policy updatePolicy(@PathVariable String id, @Valid @RequestBody PolicyRequest request) {
        return service.savePolicy(
                id,
                request.settings(),
                request.timeoutSeconds(),
                request.fallbackEnabled(),
                request.streamingEnabled(),
                request.maxRetry(),
                request.retryStrategy(),
                request.retryIntervalMs()
        );
    }

    @GetMapping("/api/v1/ai/admin/gateways/{id}/traces")
    public List<Trace> traces(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return service.traces(id, limit);
    }

    @GetMapping("/api/v1/ai/admin/gateways/{id}/dashboard")
    public Dashboard dashboard(@PathVariable String id) {
        return service.dashboard(id);
    }

    @PostMapping("/api/v1/ai/gateway/invoke")
    public InvocationResult invoke(@Valid @RequestBody InvokeRequest request) {
        return service.invoke(new Invocation(
                request.requestId(),
                request.traceId(),
                request.sceneCode(),
                request.aliasCode(),
                request.input(),
                request.parameters(),
                request.cacheable(),
                request.streaming(),
                null
        ));
    }

    private Route saveRoute(String routeId, String gatewayId, RouteRequest request) {
        return service.saveRoute(
                routeId,
                gatewayId,
                request.sceneCode(),
                request.aliasCode(),
                request.modelId(),
                request.providerId(),
                request.routingStrategy(),
                request.priority(),
                request.weight(),
                request.localPreferred(),
                request.enabled()
        );
    }

    public record RouteRequest(
            @Size(max = 100) String sceneCode,
            @NotBlank @Size(max = 100) String aliasCode,
            @Size(max = 64) String modelId,
            @Size(max = 64) String providerId,
            @Size(max = 40) String routingStrategy,
            @Min(0) int priority,
            @Min(1) @Max(1000) int weight,
            boolean localPreferred,
            boolean enabled
    ) {
    }

    public record PolicyRequest(
            Map<String, Object> settings,
            @Min(1) @Max(300) int timeoutSeconds,
            boolean fallbackEnabled,
            boolean streamingEnabled,
            @Min(0) @Max(10) int maxRetry,
            @Size(max = 32) String retryStrategy,
            @Min(0) @Max(60000) long retryIntervalMs
    ) {
    }

    public record InvokeRequest(
            @Size(max = 100) String requestId,
            @Size(max = 100) String traceId,
            @Size(max = 100) String sceneCode,
            @NotBlank @Size(max = 100) String aliasCode,
            @Size(max = 1000000) String input,
            Map<String, Object> parameters,
            boolean cacheable,
            boolean streaming
    ) {
    }
}
