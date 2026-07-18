package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AgentModels.Agent;
import io.coreplatform.ai.application.domain.AgentModels.Approval;
import io.coreplatform.ai.application.domain.AgentModels.Definition;
import io.coreplatform.ai.application.domain.AgentModels.Execution;
import io.coreplatform.ai.application.domain.AgentModels.KnowledgeBinding;
import io.coreplatform.ai.application.domain.AgentModels.MemoryPolicy;
import io.coreplatform.ai.application.domain.AgentModels.Planner;
import io.coreplatform.ai.application.domain.AgentModels.Profile;
import io.coreplatform.ai.application.domain.AgentModels.Schedule;
import io.coreplatform.ai.application.domain.AgentModels.Task;
import io.coreplatform.ai.application.domain.AgentModels.ToolBinding;
import io.coreplatform.ai.application.domain.AgentModels.TraceStep;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.AgentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAgentRepository implements AgentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAgentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_agent WHERE code = :code",
                Map.of("code", code),
                Long.class
        ) > 0;
    }

    @Override
    public List<Agent> search(String query) {
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT id, code, name, description, status, owner_user, scene_code,
                       icon, color, tags_json, current_version, published_version,
                       create_time, update_time, create_user, update_user
                  FROM ai_agent
                 WHERE :query IS NULL OR LOWER(code) LIKE :query OR LOWER(name) LIKE :query
                    OR LOWER(COALESCE(description, '')) LIKE :query
                 ORDER BY update_time DESC, code
                """, new MapSqlParameterSource("query", normalized), this::mapAgent);
    }

    @Override
    public Optional<Agent> findAgent(String id) {
        return findAgentBy("id", id);
    }

    @Override
    public Optional<Agent> findAgentByCode(String code) {
        return findAgentBy("code", code);
    }

    @Override
    public void insertDefinition(Definition definition, String snapshotJson) {
        jdbc.update("""
                INSERT INTO ai_agent(
                    id, code, name, description, status, owner_user, scene_code,
                    icon, color, tags_json, current_version, published_version,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :status, :ownerUser, :sceneCode,
                    :icon, :color, :tags, :currentVersion, :publishedVersion,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, agentParameters(definition.agent()));
        insertRelated(definition);
        insertVersion(definition.agent(), snapshotJson);
    }

    @Override
    public void updateDefinition(Definition definition, String snapshotJson) {
        updateAgent(definition.agent());
        String agentId = definition.agent().id();
        jdbc.update("DELETE FROM ai_agent_task WHERE agent_id = :id", Map.of("id", agentId));
        jdbc.update("DELETE FROM ai_agent_tool WHERE agent_id = :id", Map.of("id", agentId));
        jdbc.update("DELETE FROM ai_agent_knowledge WHERE agent_id = :id", Map.of("id", agentId));
        jdbc.update("DELETE FROM ai_agent_profile WHERE agent_id = :id", Map.of("id", agentId));
        jdbc.update("DELETE FROM ai_agent_planner WHERE agent_id = :id", Map.of("id", agentId));
        jdbc.update("DELETE FROM ai_agent_memory WHERE agent_id = :id", Map.of("id", agentId));
        insertRelated(definition);
        insertVersion(definition.agent(), snapshotJson);
    }

    @Override
    public Definition findDefinition(String agentId) {
        Agent agent = findAgent(agentId).orElseThrow(() -> new ProviderOperationException(
                "AI_AGENT_NOT_FOUND", "Agent not found", 404
        ));
        Profile profile = jdbc.query("""
                SELECT id, agent_id, role_text, goal_text, personality, style, language, constraint_text
                  FROM ai_agent_profile
                 WHERE agent_id = :agentId
                """, Map.of("agentId", agentId), this::mapProfile).stream().findFirst()
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_AGENT_PROFILE_NOT_FOUND", "Agent Profile not found", 404
                ));
        Planner planner = jdbc.query("""
                SELECT id, agent_id, planner_type, config_json, max_steps, max_depth, retry_count
                  FROM ai_agent_planner
                 WHERE agent_id = :agentId
                """, Map.of("agentId", agentId), this::mapPlanner).stream().findFirst()
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_AGENT_PLANNER_NOT_FOUND", "Agent Planner not found", 404
                ));
        List<Task> tasks = jdbc.query("""
                SELECT id, agent_id, name, order_no, task_type, reference_id,
                       execution_mode, condition_json, config_json, enabled
                  FROM ai_agent_task
                 WHERE agent_id = :agentId
                 ORDER BY order_no
                """, Map.of("agentId", agentId), this::mapTask);
        List<ToolBinding> tools = jdbc.query("""
                SELECT tool_id, permission, approval_required
                  FROM ai_agent_tool
                 WHERE agent_id = :agentId
                 ORDER BY tool_id
                """, Map.of("agentId", agentId), (rs, rowNum) -> new ToolBinding(
                rs.getString("tool_id"), rs.getString("permission"),
                rs.getBoolean("approval_required")
        ));
        List<KnowledgeBinding> knowledge = jdbc.query("""
                SELECT knowledge_id, required
                  FROM ai_agent_knowledge
                 WHERE agent_id = :agentId
                 ORDER BY knowledge_id
                """, Map.of("agentId", agentId), (rs, rowNum) -> new KnowledgeBinding(
                rs.getString("knowledge_id"), rs.getBoolean("required")
        ));
        MemoryPolicy memory = jdbc.query("""
                SELECT memory_policy, owner_types_json, write_enabled, max_items, config_json
                  FROM ai_agent_memory
                 WHERE agent_id = :agentId
                """, Map.of("agentId", agentId), (rs, rowNum) -> new MemoryPolicy(
                rs.getString("memory_policy"),
                stringList(rs.getString("owner_types_json")),
                rs.getBoolean("write_enabled"),
                rs.getInt("max_items"),
                objectMap(rs.getString("config_json"))
        )).stream().findFirst().orElse(new MemoryPolicy("NONE", List.of(), false, 0, Map.of()));
        return new Definition(agent, profile, planner, tasks, tools, knowledge, memory);
    }

    @Override
    public void updateAgent(Agent value) {
        jdbc.update("""
                UPDATE ai_agent
                   SET name = :name, description = :description, status = :status,
                       scene_code = :sceneCode, icon = :icon, color = :color,
                       tags_json = :tags, current_version = :currentVersion,
                       published_version = :publishedVersion,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, agentParameters(value));
    }

    @Override
    public void markVersionTested(String agentId, int version, boolean passed, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_agent_version
                   SET tests_passed = :passed, update_time = :now, update_user = :actor
                 WHERE agent_id = :agentId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("passed", passed)
                .addValue("now", now.toString())
                .addValue("actor", actor)
                .addValue("agentId", agentId)
                .addValue("version", version));
    }

    @Override
    public boolean isVersionTested(String agentId, int version) {
        Boolean value = jdbc.queryForObject("""
                SELECT tests_passed
                  FROM ai_agent_version
                 WHERE agent_id = :agentId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("agentId", agentId)
                .addValue("version", version), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public void markVersionPublished(String agentId, int version, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_agent_version
                   SET published_time = :now, update_time = :now, update_user = :actor
                 WHERE agent_id = :agentId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("now", now.toString())
                .addValue("actor", actor)
                .addValue("agentId", agentId)
                .addValue("version", version));
    }

    @Override
    public Execution insertExecution(Execution value) {
        jdbc.update("""
                INSERT INTO ai_agent_execution(
                    id, agent_id, agent_version, conversation_id, goal, status,
                    current_task_no, result, error_code, trace_id, started_at, ended_at,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :agentId, :agentVersion, :conversationId, :goal, :status,
                    :currentTaskNo, :result, :errorCode, :traceId, :startedAt, :endedAt,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, executionParameters(value));
        return value;
    }

    @Override
    public void updateExecution(Execution value) {
        jdbc.update("""
                UPDATE ai_agent_execution
                   SET status = :status, current_task_no = :currentTaskNo,
                       result = :result, error_code = :errorCode,
                       started_at = :startedAt, ended_at = :endedAt,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, executionParameters(value));
    }

    @Override
    public Optional<Execution> findExecution(String id) {
        return jdbc.query(executionSelect() + " WHERE id = :id", Map.of("id", id), this::mapExecution)
                .stream().findFirst();
    }

    @Override
    public List<Execution> findExecutions(String agentId, int limit) {
        return jdbc.query(executionSelect() + """
                 WHERE agent_id = :agentId
                 ORDER BY create_time DESC
                 LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("agentId", agentId)
                .addValue("limit", Math.max(1, Math.min(limit, 500))), this::mapExecution);
    }

    @Override
    public TraceStep insertTrace(TraceStep value) {
        jdbc.update("""
                INSERT INTO ai_agent_trace(
                    id, execution_id, step_no, stage, action, result, status,
                    latency_ms, metadata_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :executionId, :stepNo, :stage, :action, :result, :status,
                    :latencyMs, :metadata,
                    :createTime, :createTime, 'agent', 'agent'
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("executionId", value.executionId())
                .addValue("stepNo", value.stepNo())
                .addValue("stage", value.stage())
                .addValue("action", value.action())
                .addValue("result", value.result())
                .addValue("status", value.status())
                .addValue("latencyMs", value.latencyMs())
                .addValue("metadata", json(value.metadata()))
                .addValue("createTime", value.createTime().toString()));
        return value;
    }

    @Override
    public List<TraceStep> findTrace(String executionId) {
        return jdbc.query("""
                SELECT id, execution_id, step_no, stage, action, result, status,
                       latency_ms, metadata_json, create_time
                  FROM ai_agent_trace
                 WHERE execution_id = :executionId
                 ORDER BY step_no
                """, Map.of("executionId", executionId), this::mapTrace);
    }

    @Override
    public Approval insertApproval(Approval value) {
        jdbc.update("""
                INSERT INTO ai_agent_approval(
                    id, execution_id, task_id, approval_type, request_detail, status,
                    approval_token, approved_by, approved_at, rejection_reason,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :executionId, :taskId, :approvalType, :requestDetail, :status,
                    :approvalToken, :approvedBy, :approvedAt, :rejectionReason,
                    :createTime, :updateTime, 'agent', 'agent'
                )
                """, approvalParameters(value));
        return value;
    }

    @Override
    public Optional<Approval> findPendingApproval(String executionId) {
        return jdbc.query("""
                SELECT id, execution_id, task_id, approval_type, request_detail, status,
                       approval_token, approved_by, approved_at, rejection_reason,
                       create_time, update_time
                  FROM ai_agent_approval
                 WHERE execution_id = :executionId AND status = 'PENDING'
                 ORDER BY create_time DESC
                 LIMIT 1
                """, Map.of("executionId", executionId), this::mapApproval).stream().findFirst();
    }

    @Override
    public void updateApproval(Approval value) {
        jdbc.update("""
                UPDATE ai_agent_approval
                   SET status = :status, approved_by = :approvedBy, approved_at = :approvedAt,
                       rejection_reason = :rejectionReason, update_time = :updateTime,
                       update_user = COALESCE(:approvedBy, 'agent')
                 WHERE id = :id
                """, approvalParameters(value));
    }

    @Override
    public Schedule saveSchedule(Schedule value) {
        MapSqlParameterSource parameters = scheduleParameters(value);
        int updated = jdbc.update("""
                UPDATE ai_agent_schedule
                   SET cron_expression = :cronExpression, goal_template = :goalTemplate,
                       enabled = :enabled, next_run_time = :nextRunTime,
                       last_run_time = :lastRunTime, update_time = :updateTime,
                       update_user = 'agent'
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_agent_schedule(
                        id, agent_id, cron_expression, goal_template, enabled,
                        next_run_time, last_run_time,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :agentId, :cronExpression, :goalTemplate, :enabled,
                        :nextRunTime, :lastRunTime,
                        :createTime, :updateTime, 'agent', 'agent'
                    )
                    """, parameters);
        }
        return value;
    }

    @Override
    public List<Schedule> findSchedules(String agentId) {
        return jdbc.query(scheduleSelect() + """
                 WHERE agent_id = :agentId
                 ORDER BY create_time
                """, Map.of("agentId", agentId), this::mapSchedule);
    }

    @Override
    public List<Schedule> findDueSchedules(Instant now) {
        return jdbc.query(scheduleSelect() + """
                 WHERE enabled = TRUE AND next_run_time IS NOT NULL AND next_run_time <= :now
                 ORDER BY next_run_time
                """, Map.of("now", now.toString()), this::mapSchedule);
    }

    private void insertRelated(Definition definition) {
        Agent agent = definition.agent();
        String now = agent.updateTime().toString();
        String actor = agent.updateUser();
        Profile profile = definition.profile();
        jdbc.update("""
                INSERT INTO ai_agent_profile(
                    id, agent_id, role_text, goal_text, personality, style, language, constraint_text,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :agentId, :role, :goal, :personality, :style, :language, :constraints,
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", profile.id())
                .addValue("agentId", agent.id())
                .addValue("role", profile.role())
                .addValue("goal", profile.goal())
                .addValue("personality", profile.personality())
                .addValue("style", profile.style())
                .addValue("language", profile.language())
                .addValue("constraints", profile.constraints())
                .addValue("now", now)
                .addValue("actor", actor));
        Planner planner = definition.planner();
        jdbc.update("""
                INSERT INTO ai_agent_planner(
                    id, agent_id, planner_type, config_json, max_steps, max_depth, retry_count,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :agentId, :plannerType, :config, :maxSteps, :maxDepth, :retryCount,
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", planner.id())
                .addValue("agentId", agent.id())
                .addValue("plannerType", planner.plannerType())
                .addValue("config", json(planner.config()))
                .addValue("maxSteps", planner.maxSteps())
                .addValue("maxDepth", planner.maxDepth())
                .addValue("retryCount", planner.retryCount())
                .addValue("now", now)
                .addValue("actor", actor));
        for (Task task : definition.tasks()) {
            jdbc.update("""
                    INSERT INTO ai_agent_task(
                        id, agent_id, name, order_no, task_type, reference_id,
                        execution_mode, condition_json, config_json, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :agentId, :name, :orderNo, :taskType, :referenceId,
                        :executionMode, :condition, :config, :enabled,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", task.id())
                    .addValue("agentId", agent.id())
                    .addValue("name", task.name())
                    .addValue("orderNo", task.orderNo())
                    .addValue("taskType", task.taskType())
                    .addValue("referenceId", task.referenceId())
                    .addValue("executionMode", task.executionMode())
                    .addValue("condition", json(task.condition()))
                    .addValue("config", json(task.config()))
                    .addValue("enabled", task.enabled())
                    .addValue("now", now)
                    .addValue("actor", actor));
        }
        for (ToolBinding binding : definition.tools()) {
            jdbc.update("""
                    INSERT INTO ai_agent_tool(
                        id, agent_id, tool_id, permission, approval_required,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :agentId, :toolId, :permission, :approvalRequired,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("agentId", agent.id())
                    .addValue("toolId", binding.toolId())
                    .addValue("permission", binding.permission())
                    .addValue("approvalRequired", binding.approvalRequired())
                    .addValue("now", now)
                    .addValue("actor", actor));
        }
        for (KnowledgeBinding binding : definition.knowledge()) {
            jdbc.update("""
                    INSERT INTO ai_agent_knowledge(
                        id, agent_id, knowledge_id, required,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :agentId, :knowledgeId, :required,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("agentId", agent.id())
                    .addValue("knowledgeId", binding.knowledgeId())
                    .addValue("required", binding.required())
                    .addValue("now", now)
                    .addValue("actor", actor));
        }
        MemoryPolicy memory = definition.memory();
        jdbc.update("""
                INSERT INTO ai_agent_memory(
                    id, agent_id, memory_policy, owner_types_json, write_enabled,
                    max_items, config_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :agentId, :memoryPolicy, :ownerTypes, :writeEnabled,
                    :maxItems, :config,
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("agentId", agent.id())
                .addValue("memoryPolicy", memory.policy())
                .addValue("ownerTypes", json(memory.ownerTypes()))
                .addValue("writeEnabled", memory.writeEnabled())
                .addValue("maxItems", memory.maxItems())
                .addValue("config", json(memory.config()))
                .addValue("now", now)
                .addValue("actor", actor));
    }

    private void insertVersion(Agent agent, String snapshotJson) {
        jdbc.update("""
                INSERT INTO ai_agent_version(
                    id, agent_id, version, config_json, tests_passed, published_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :agentId, :version, :config, FALSE, NULL,
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("agentId", agent.id())
                .addValue("version", agent.currentVersion())
                .addValue("config", snapshotJson)
                .addValue("now", agent.updateTime().toString())
                .addValue("actor", agent.updateUser()));
    }

    private Optional<Agent> findAgentBy(String column, String value) {
        return jdbc.query("""
                SELECT id, code, name, description, status, owner_user, scene_code,
                       icon, color, tags_json, current_version, published_version,
                       create_time, update_time, create_user, update_user
                  FROM ai_agent
                 WHERE """ + " " + column + " = :value", Map.of("value", value), this::mapAgent)
                .stream().findFirst();
    }

    private Agent mapAgent(ResultSet rs, int rowNum) throws SQLException {
        return new Agent(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), rs.getString("owner_user"),
                rs.getString("scene_code"), rs.getString("icon"), rs.getString("color"),
                stringList(rs.getString("tags_json")), rs.getInt("current_version"),
                nullableInteger(rs, "published_version"), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Profile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new Profile(
                rs.getString("id"), rs.getString("agent_id"), rs.getString("role_text"),
                rs.getString("goal_text"), rs.getString("personality"), rs.getString("style"),
                rs.getString("language"), rs.getString("constraint_text")
        );
    }

    private Planner mapPlanner(ResultSet rs, int rowNum) throws SQLException {
        return new Planner(
                rs.getString("id"), rs.getString("agent_id"), rs.getString("planner_type"),
                objectMap(rs.getString("config_json")), rs.getInt("max_steps"),
                rs.getInt("max_depth"), rs.getInt("retry_count")
        );
    }

    private Task mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new Task(
                rs.getString("id"), rs.getString("agent_id"), rs.getString("name"),
                rs.getInt("order_no"), rs.getString("task_type"), rs.getString("reference_id"),
                rs.getString("execution_mode"), objectMap(rs.getString("condition_json")),
                objectMap(rs.getString("config_json")), rs.getBoolean("enabled")
        );
    }

    private Execution mapExecution(ResultSet rs, int rowNum) throws SQLException {
        return new Execution(
                rs.getString("id"), rs.getString("agent_id"), rs.getInt("agent_version"),
                rs.getString("conversation_id"), rs.getString("goal"), rs.getString("status"),
                rs.getInt("current_task_no"), rs.getString("result"), rs.getString("error_code"),
                rs.getString("trace_id"), parseInstant(rs.getString("started_at")),
                parseInstant(rs.getString("ended_at")), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private TraceStep mapTrace(ResultSet rs, int rowNum) throws SQLException {
        return new TraceStep(
                rs.getString("id"), rs.getString("execution_id"), rs.getInt("step_no"),
                rs.getString("stage"), rs.getString("action"), rs.getString("result"),
                rs.getString("status"), rs.getLong("latency_ms"),
                objectMap(rs.getString("metadata_json")), Instant.parse(rs.getString("create_time"))
        );
    }

    private Approval mapApproval(ResultSet rs, int rowNum) throws SQLException {
        return new Approval(
                rs.getString("id"), rs.getString("execution_id"), rs.getString("task_id"),
                rs.getString("approval_type"), rs.getString("request_detail"),
                rs.getString("status"), rs.getString("approval_token"), rs.getString("approved_by"),
                parseInstant(rs.getString("approved_at")), rs.getString("rejection_reason"),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time"))
        );
    }

    private Schedule mapSchedule(ResultSet rs, int rowNum) throws SQLException {
        return new Schedule(
                rs.getString("id"), rs.getString("agent_id"), rs.getString("cron_expression"),
                rs.getString("goal_template"), rs.getBoolean("enabled"),
                parseInstant(rs.getString("next_run_time")), parseInstant(rs.getString("last_run_time")),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time"))
        );
    }

    private MapSqlParameterSource agentParameters(Agent value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("code", value.code())
                .addValue("name", value.name())
                .addValue("description", value.description())
                .addValue("status", value.status())
                .addValue("ownerUser", value.ownerUser())
                .addValue("sceneCode", value.sceneCode())
                .addValue("icon", value.icon())
                .addValue("color", value.color())
                .addValue("tags", json(value.tags()))
                .addValue("currentVersion", value.currentVersion())
                .addValue("publishedVersion", value.publishedVersion())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource executionParameters(Execution value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("agentId", value.agentId())
                .addValue("agentVersion", value.agentVersion())
                .addValue("conversationId", value.conversationId())
                .addValue("goal", value.goal())
                .addValue("status", value.status())
                .addValue("currentTaskNo", value.currentTaskNo())
                .addValue("result", value.result())
                .addValue("errorCode", value.errorCode())
                .addValue("traceId", value.traceId())
                .addValue("startedAt", instant(value.startedAt()))
                .addValue("endedAt", instant(value.endedAt()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource approvalParameters(Approval value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("executionId", value.executionId())
                .addValue("taskId", value.taskId())
                .addValue("approvalType", value.approvalType())
                .addValue("requestDetail", value.requestDetail())
                .addValue("status", value.status())
                .addValue("approvalToken", value.approvalToken())
                .addValue("approvedBy", value.approvedBy())
                .addValue("approvedAt", instant(value.approvedAt()))
                .addValue("rejectionReason", value.rejectionReason())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString());
    }

    private MapSqlParameterSource scheduleParameters(Schedule value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("agentId", value.agentId())
                .addValue("cronExpression", value.cronExpression())
                .addValue("goalTemplate", value.goalTemplate())
                .addValue("enabled", value.enabled())
                .addValue("nextRunTime", instant(value.nextRunTime()))
                .addValue("lastRunTime", instant(value.lastRunTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString());
    }

    private String executionSelect() {
        return """
                SELECT id, agent_id, agent_version, conversation_id, goal, status,
                       current_task_no, result, error_code, trace_id, started_at, ended_at,
                       create_time, update_time, create_user, update_user
                  FROM ai_agent_execution
                """;
    }

    private String scheduleSelect() {
        return """
                SELECT id, agent_id, cron_expression, goal_template, enabled,
                       next_run_time, last_run_time, create_time, update_time
                  FROM ai_agent_schedule
                """;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Agent data", exception);
        }
    }

    private Map<String, Object> objectMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Agent data", exception);
        }
    }

    private List<String> stringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Agent list", exception);
        }
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }
}
