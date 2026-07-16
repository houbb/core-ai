package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.GatewayModels.Dashboard;
import io.coreplatform.ai.application.domain.GatewayModels.Gateway;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.domain.GatewayModels.Policy;
import io.coreplatform.ai.application.domain.GatewayModels.Route;
import io.coreplatform.ai.application.domain.GatewayModels.Trace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GatewayRepository {

    Gateway defaultGateway();

    List<Gateway> findGateways();

    Policy findPolicy(String gatewayId);

    Policy savePolicy(Policy policy, Instant now, String actor);

    List<Route> findRoutes(String gatewayId, String sceneCode, String aliasCode);

    Route saveRoute(Route route);

    Optional<InvocationResult> findCache(String gatewayId, String cacheKey, Instant now);

    void saveCache(String gatewayId, String sceneCode, String cacheKey, InvocationResult result, Instant expiresAt);

    boolean rateLimitAllowed(String gatewayId, String actor, String sceneCode, Instant since);

    void insertTrace(String gatewayId, InvocationResult result);

    List<Trace> findTraces(String gatewayId, int limit);

    Dashboard dashboard(String gatewayId, Instant from);
}
