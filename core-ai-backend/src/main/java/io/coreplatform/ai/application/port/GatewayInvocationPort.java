package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;

public interface GatewayInvocationPort {

    InvocationResult invoke(Invocation invocation);
}
