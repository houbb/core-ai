package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.PromptReference;
import io.coreplatform.ai.application.domain.PromptRenderResult;

import java.util.Map;

public interface PromptReferencePort {

    PromptReference resolvePublished(String reference, Integer version);

    PromptRenderResult renderPublished(
            String reference,
            Integer version,
            Map<String, Object> variables
    );
}
