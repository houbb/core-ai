package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Task;
import io.coreplatform.ai.application.port.AgentPlannerPort;

import java.util.Comparator;
import java.util.List;

public class DeterministicAgentPlannerAdapter implements AgentPlannerPort {

    @Override
    public List<Task> plan(Definition definition, String goal) {
        return definition.tasks().stream()
                .filter(Task::enabled)
                .sorted(Comparator.comparingInt(Task::orderNo))
                .limit(definition.planner().maxSteps())
                .toList();
    }

    @Override
    public String mode() {
        return "DETERMINISTIC";
    }
}
