package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.PromptData;

public interface PromptPermissionPort {

    boolean canRead(PromptData prompt);

    boolean canManage(PromptData prompt);
}
