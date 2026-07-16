package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ToolModels.ExecutorRequest;
import io.coreplatform.ai.application.domain.ToolModels.ExecutorResult;

public interface ToolExecutionPort {

    ExecutorResult execute(ExecutorRequest request);
}
