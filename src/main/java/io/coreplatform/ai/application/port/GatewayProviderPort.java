package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.GatewayModels.ProviderRequest;
import io.coreplatform.ai.application.domain.GatewayModels.ProviderResult;

public interface GatewayProviderPort {

    ProviderResult execute(ProviderRequest request);
}
