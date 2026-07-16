package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.AgentModels.Agent;
import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.MemoryPolicy;
import io.coreplatform.ai.application.domain.AgentModels.Planner;
import io.coreplatform.ai.application.domain.AgentModels.Profile;
import io.coreplatform.ai.application.domain.AgentModels.Task;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAgentPlannerAdapterTest {

    @Test
    void shouldFilterSortAndLimitEnabledTasks() {
        Instant now = Instant.now();
        Agent agent = new Agent(
                "agent", "agent", "Agent", null, "DRAFT", "tester", null,
                null, null, List.of(), 1, null, now, now, "tester", "tester"
        );
        Definition definition = new Definition(
                agent,
                new Profile("profile", "agent", "role", "goal", null, null, "en", null),
                new Planner("planner", "agent", "DETERMINISTIC", Map.of(), 2, 3, 0),
                List.of(
                        task(3, true),
                        task(1, true),
                        task(2, false),
                        task(4, true)
                ),
                List.of(),
                List.of(),
                new MemoryPolicy("NONE", List.of(), false, 0, Map.of())
        );

        DeterministicAgentPlannerAdapter adapter = new DeterministicAgentPlannerAdapter();
        assertThat(adapter.plan(definition, "goal"))
                .extracting(Task::orderNo)
                .containsExactly(1, 3);
        assertThat(adapter.mode()).isEqualTo("DETERMINISTIC");
    }

    private Task task(int order, boolean enabled) {
        return new Task(
                "task-" + order, "agent", "Task " + order, order, "GATEWAY",
                "default", "SERIAL", Map.of(), Map.of(), enabled
        );
    }
}
