package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AgentModels.Agent;
import io.coreplatform.ai.application.domain.AgentModels.Approval;
import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Execution;
import io.coreplatform.ai.application.domain.AgentModels.Schedule;
import io.coreplatform.ai.application.domain.AgentModels.TraceStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentRepository {

    boolean existsByCode(String code);

    List<Agent> search(String query);

    Optional<Agent> findAgent(String id);

    Optional<Agent> findAgentByCode(String code);

    void insertDefinition(Definition definition, String snapshotJson);

    void updateDefinition(Definition definition, String snapshotJson);

    Definition findDefinition(String agentId);

    void updateAgent(Agent agent);

    void markVersionTested(String agentId, int version, boolean passed, Instant now, String actor);

    boolean isVersionTested(String agentId, int version);

    void markVersionPublished(String agentId, int version, Instant now, String actor);

    Execution insertExecution(Execution execution);

    void updateExecution(Execution execution);

    Optional<Execution> findExecution(String id);

    List<Execution> findExecutions(String agentId, int limit);

    TraceStep insertTrace(TraceStep trace);

    List<TraceStep> findTrace(String executionId);

    Approval insertApproval(Approval approval);

    Optional<Approval> findPendingApproval(String executionId);

    void updateApproval(Approval approval);

    Schedule saveSchedule(Schedule schedule);

    List<Schedule> findSchedules(String agentId);

    List<Schedule> findDueSchedules(Instant now);
}
