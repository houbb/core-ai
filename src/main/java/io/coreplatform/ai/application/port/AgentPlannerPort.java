package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Task;

import java.util.List;

public interface AgentPlannerPort {

    List<Task> plan(Definition definition, String goal);

    String mode();
}
