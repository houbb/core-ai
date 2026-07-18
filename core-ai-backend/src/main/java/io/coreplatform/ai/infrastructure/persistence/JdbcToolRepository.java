package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.ToolModels.Execution;
import io.coreplatform.ai.application.domain.ToolModels.MarketItem;
import io.coreplatform.ai.application.domain.ToolModels.Parameter;
import io.coreplatform.ai.application.domain.ToolModels.Policy;
import io.coreplatform.ai.application.domain.ToolModels.Status;
import io.coreplatform.ai.application.domain.ToolModels.TestCase;
import io.coreplatform.ai.application.domain.ToolModels.Tool;
import io.coreplatform.ai.application.domain.ToolModels.Version;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ToolRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcToolRepository implements ToolRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcToolRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_tool WHERE code = :code",
                Map.of("code", code),
                Long.class
        ) > 0;
    }

    @Override
    public List<Tool> search(String query) {
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT id, code, name, description, category, tool_type, icon, owner_user,
                       status, current_version, published_version,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool
                 WHERE :query IS NULL
                    OR LOWER(code) LIKE :query OR LOWER(name) LIKE :query
                    OR LOWER(COALESCE(description, '')) LIKE :query
                 ORDER BY update_time DESC, code
                """, new MapSqlParameterSource("query", normalized), this::mapTool);
    }

    @Override
    public Optional<Tool> findTool(String id) {
        return findToolBy("id", id);
    }

    @Override
    public Optional<Tool> findToolByCode(String code) {
        return findToolBy("code", code);
    }

    @Override
    public void insertTool(Tool value) {
        jdbc.update("""
                INSERT INTO ai_tool(
                    id, code, name, description, category, tool_type, icon, owner_user,
                    status, current_version, published_version,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :category, :toolType, :icon, :ownerUser,
                    :status, :currentVersion, :publishedVersion,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, toolParameters(value));
    }

    @Override
    public void updateTool(Tool value) {
        jdbc.update("""
                UPDATE ai_tool
                   SET name = :name, description = :description, category = :category,
                       tool_type = :toolType, icon = :icon, status = :status,
                       current_version = :currentVersion, published_version = :publishedVersion,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, toolParameters(value));
    }

    @Override
    public void insertVersion(Version value) {
        jdbc.update("""
                INSERT INTO ai_tool_version(
                    id, tool_id, version, schema_json, output_schema_json,
                    executor_type, executor_config_json, chain_json, change_log,
                    tests_passed, last_tested_time, published_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :toolId, :version, :schemaJson, :outputSchemaJson,
                    :executorType, :executorConfig, :chain, :changeLog,
                    :testsPassed, :lastTestedTime, :publishedTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, versionParameters(value));
        for (Parameter parameter : value.parameters()) {
            jdbc.update("""
                    INSERT INTO ai_tool_parameter(
                        id, tool_version_id, name, parameter_type, required,
                        default_value, validation_rule, description,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :toolVersionId, :name, :parameterType, :required,
                        :defaultValue, :validationRule, :description,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameterParameters(parameter));
        }
    }

    @Override
    public Optional<Version> findVersion(String toolId, int version) {
        return jdbc.query("""
                SELECT id, tool_id, version, schema_json, output_schema_json,
                       executor_type, executor_config_json, chain_json, change_log,
                       tests_passed, last_tested_time, published_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool_version
                 WHERE tool_id = :toolId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("toolId", toolId)
                .addValue("version", version), this::mapVersion)
                .stream().findFirst().map(this::attachParameters);
    }

    @Override
    public List<Version> findVersions(String toolId) {
        return jdbc.query("""
                SELECT id, tool_id, version, schema_json, output_schema_json,
                       executor_type, executor_config_json, chain_json, change_log,
                       tests_passed, last_tested_time, published_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool_version
                 WHERE tool_id = :toolId
                 ORDER BY version DESC
                """, Map.of("toolId", toolId), this::mapVersion).stream()
                .map(this::attachParameters)
                .toList();
    }

    @Override
    public Policy findPolicy(String toolId) {
        return jdbc.query("""
                SELECT id, tool_id, access_level, readonly, manual_confirm,
                       approval_required, timeout_seconds, retry_count, log_content,
                       retention_days, create_time, update_time, create_user, update_user
                  FROM ai_tool_policy
                 WHERE tool_id = :toolId
                """, Map.of("toolId", toolId), this::mapPolicy).stream().findFirst()
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_TOOL_POLICY_NOT_FOUND", "Tool Policy not found", 404
                ));
    }

    @Override
    public void savePolicy(Policy value) {
        MapSqlParameterSource parameters = policyParameters(value);
        int updated = jdbc.update("""
                UPDATE ai_tool_policy
                   SET access_level = :accessLevel, readonly = :readonly,
                       manual_confirm = :manualConfirm, approval_required = :approvalRequired,
                       timeout_seconds = :timeoutSeconds, retry_count = :retryCount,
                       log_content = :logContent, retention_days = :retentionDays,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_tool_policy(
                        id, tool_id, access_level, readonly, manual_confirm,
                        approval_required, timeout_seconds, retry_count, log_content,
                        retention_days, create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :toolId, :accessLevel, :readonly, :manualConfirm,
                        :approvalRequired, :timeoutSeconds, :retryCount, :logContent,
                        :retentionDays, :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
    }

    @Override
    public TestCase insertTestCase(TestCase value) {
        jdbc.update("""
                INSERT INTO ai_tool_testcase(
                    id, tool_version_id, name, input_json, expected_result, enabled,
                    last_actual_result, last_passed, last_run_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :versionId, :name, :inputJson, :expectedResult, :enabled,
                    :lastActualResult, :lastPassed, :lastRunTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, testParameters(value));
        return value;
    }

    @Override
    public List<TestCase> findTestCases(String versionId) {
        return jdbc.query("""
                SELECT id, tool_version_id, name, input_json, expected_result, enabled,
                       last_actual_result, last_passed, last_run_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool_testcase
                 WHERE tool_version_id = :versionId
                 ORDER BY create_time, id
                """, Map.of("versionId", versionId), this::mapTest);
    }

    @Override
    public void updateTestResult(TestCase value) {
        jdbc.update("""
                UPDATE ai_tool_testcase
                   SET last_actual_result = :lastActualResult, last_passed = :lastPassed,
                       last_run_time = :lastRunTime, update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, testParameters(value));
    }

    @Override
    public void markVersionTested(String versionId, boolean passed, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_tool_version
                   SET tests_passed = :passed, last_tested_time = :now,
                       update_time = :now, update_user = :actor
                 WHERE id = :versionId
                """, new MapSqlParameterSource()
                .addValue("passed", passed)
                .addValue("now", now.toString())
                .addValue("actor", actor)
                .addValue("versionId", versionId));
    }

    @Override
    public void markVersionPublished(String versionId, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_tool_version
                   SET published_time = :now, update_time = :now, update_user = :actor
                 WHERE id = :versionId
                """, new MapSqlParameterSource()
                .addValue("now", now.toString())
                .addValue("actor", actor)
                .addValue("versionId", versionId));
    }

    @Override
    public Execution insertExecution(Execution value) {
        jdbc.update("""
                INSERT INTO ai_tool_execution_log(
                    id, tool_id, tool_version, request_json, response_json, request_hash,
                    status, mode, approval_token, confirmed_by, approved_by, latency_ms,
                    error_code, error_message, trace_id, expire_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :toolId, :toolVersion, :requestJson, :responseJson, :requestHash,
                    :status, :mode, :approvalToken, :confirmedBy, :approvedBy, :latencyMs,
                    :errorCode, :errorMessage, :traceId, :expireTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, executionParameters(value));
        return value;
    }

    @Override
    public void updateExecution(Execution value) {
        jdbc.update("""
                UPDATE ai_tool_execution_log
                   SET response_json = :responseJson, status = :status, mode = :mode,
                       confirmed_by = :confirmedBy, approved_by = :approvedBy,
                       latency_ms = :latencyMs, error_code = :errorCode,
                       error_message = :errorMessage, update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, executionParameters(value));
    }

    @Override
    public Optional<Execution> findExecution(String id) {
        return jdbc.query(executionSelect() + " WHERE id = :id", Map.of("id", id), this::mapExecution)
                .stream().findFirst();
    }

    @Override
    public List<Execution> findExecutions(String toolId, int limit) {
        return jdbc.query(executionSelect() + """
                 WHERE tool_id = :toolId
                 ORDER BY create_time DESC
                 LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("toolId", toolId)
                .addValue("limit", Math.max(1, Math.min(limit, 500))), this::mapExecution);
    }

    @Override
    public List<MarketItem> findMarket() {
        return jdbc.query("""
                SELECT id, tool_name, tool_code, publisher, version, category,
                       description, manifest_json, install_count, builtin
                  FROM ai_tool_market
                 ORDER BY builtin DESC, install_count DESC, tool_name
                """, Map.of(), this::mapMarket);
    }

    @Override
    public Optional<MarketItem> findMarketItem(String id) {
        return jdbc.query("""
                SELECT id, tool_name, tool_code, publisher, version, category,
                       description, manifest_json, install_count, builtin
                  FROM ai_tool_market
                 WHERE id = :id
                """, Map.of("id", id), this::mapMarket).stream().findFirst();
    }

    @Override
    public void incrementInstall(String id, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_tool_market
                   SET install_count = install_count + 1, update_time = :now, update_user = :actor
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("now", now.toString())
                .addValue("actor", actor));
    }

    private Optional<Tool> findToolBy(String column, String value) {
        return jdbc.query("""
                SELECT id, code, name, description, category, tool_type, icon, owner_user,
                       status, current_version, published_version,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool
                 WHERE """ + " " + column + " = :value", Map.of("value", value), this::mapTool)
                .stream().findFirst();
    }

    private Version attachParameters(Version value) {
        List<Parameter> parameters = jdbc.query("""
                SELECT id, tool_version_id, name, parameter_type, required,
                       default_value, validation_rule, description,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool_parameter
                 WHERE tool_version_id = :versionId
                 ORDER BY name
                """, Map.of("versionId", value.id()), this::mapParameter);
        return new Version(
                value.id(), value.toolId(), value.version(), value.schemaJson(),
                value.outputSchemaJson(), value.executorType(), value.executorConfig(),
                value.chain(), value.changeLog(), value.testsPassed(), value.lastTestedTime(),
                value.publishedTime(), parameters, value.createTime(), value.updateTime(),
                value.createUser(), value.updateUser()
        );
    }

    private Tool mapTool(ResultSet rs, int rowNum) throws SQLException {
        return new Tool(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("tool_type"),
                rs.getString("icon"),
                rs.getString("owner_user"),
                Status.valueOf(rs.getString("status")),
                rs.getInt("current_version"),
                nullableInteger(rs, "published_version"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Version mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new Version(
                rs.getString("id"),
                rs.getString("tool_id"),
                rs.getInt("version"),
                rs.getString("schema_json"),
                rs.getString("output_schema_json"),
                rs.getString("executor_type"),
                objectMap(rs.getString("executor_config_json")),
                stringList(rs.getString("chain_json")),
                rs.getString("change_log"),
                rs.getBoolean("tests_passed"),
                parseInstant(rs.getString("last_tested_time")),
                parseInstant(rs.getString("published_time")),
                List.of(),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Parameter mapParameter(ResultSet rs, int rowNum) throws SQLException {
        return new Parameter(
                rs.getString("id"),
                rs.getString("tool_version_id"),
                rs.getString("name"),
                rs.getString("parameter_type"),
                rs.getBoolean("required"),
                rs.getString("default_value"),
                rs.getString("validation_rule"),
                rs.getString("description"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Policy mapPolicy(ResultSet rs, int rowNum) throws SQLException {
        return new Policy(
                rs.getString("id"),
                rs.getString("tool_id"),
                rs.getString("access_level"),
                rs.getBoolean("readonly"),
                rs.getBoolean("manual_confirm"),
                rs.getBoolean("approval_required"),
                rs.getInt("timeout_seconds"),
                rs.getInt("retry_count"),
                rs.getBoolean("log_content"),
                rs.getInt("retention_days"),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private TestCase mapTest(ResultSet rs, int rowNum) throws SQLException {
        return new TestCase(
                rs.getString("id"),
                rs.getString("tool_version_id"),
                rs.getString("name"),
                rs.getString("input_json"),
                rs.getString("expected_result"),
                rs.getBoolean("enabled"),
                rs.getString("last_actual_result"),
                nullableBoolean(rs, "last_passed"),
                parseInstant(rs.getString("last_run_time")),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Execution mapExecution(ResultSet rs, int rowNum) throws SQLException {
        return new Execution(
                rs.getString("id"),
                rs.getString("tool_id"),
                rs.getInt("tool_version"),
                objectMap(rs.getString("request_json")),
                object(rs.getString("response_json")),
                rs.getString("request_hash"),
                rs.getString("status"),
                rs.getString("mode"),
                rs.getString("approval_token"),
                rs.getString("confirmed_by"),
                rs.getString("approved_by"),
                rs.getLong("latency_ms"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getString("trace_id"),
                parseInstant(rs.getString("expire_time")),
                Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private MarketItem mapMarket(ResultSet rs, int rowNum) throws SQLException {
        return new MarketItem(
                rs.getString("id"),
                rs.getString("tool_name"),
                rs.getString("tool_code"),
                rs.getString("publisher"),
                rs.getString("version"),
                rs.getString("category"),
                rs.getString("description"),
                objectMap(rs.getString("manifest_json")),
                rs.getLong("install_count"),
                rs.getBoolean("builtin")
        );
    }

    private MapSqlParameterSource toolParameters(Tool value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("code", value.code())
                .addValue("name", value.name())
                .addValue("description", value.description())
                .addValue("category", value.category())
                .addValue("toolType", value.toolType())
                .addValue("icon", value.icon())
                .addValue("ownerUser", value.ownerUser())
                .addValue("status", value.status().name())
                .addValue("currentVersion", value.currentVersion())
                .addValue("publishedVersion", value.publishedVersion())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource versionParameters(Version value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("toolId", value.toolId())
                .addValue("version", value.version())
                .addValue("schemaJson", value.schemaJson())
                .addValue("outputSchemaJson", value.outputSchemaJson())
                .addValue("executorType", value.executorType())
                .addValue("executorConfig", json(value.executorConfig()))
                .addValue("chain", json(value.chain()))
                .addValue("changeLog", value.changeLog())
                .addValue("testsPassed", value.testsPassed())
                .addValue("lastTestedTime", instant(value.lastTestedTime()))
                .addValue("publishedTime", instant(value.publishedTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource parameterParameters(Parameter value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("toolVersionId", value.toolVersionId())
                .addValue("name", value.name())
                .addValue("parameterType", value.type())
                .addValue("required", value.required())
                .addValue("defaultValue", value.defaultValue())
                .addValue("validationRule", value.validationRule())
                .addValue("description", value.description())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource policyParameters(Policy value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("toolId", value.toolId())
                .addValue("accessLevel", value.accessLevel())
                .addValue("readonly", value.readonly())
                .addValue("manualConfirm", value.manualConfirm())
                .addValue("approvalRequired", value.approvalRequired())
                .addValue("timeoutSeconds", value.timeoutSeconds())
                .addValue("retryCount", value.retryCount())
                .addValue("logContent", value.logContent())
                .addValue("retentionDays", value.retentionDays())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource testParameters(TestCase value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("versionId", value.toolVersionId())
                .addValue("name", value.name())
                .addValue("inputJson", value.inputJson())
                .addValue("expectedResult", value.expectedResult())
                .addValue("enabled", value.enabled())
                .addValue("lastActualResult", value.lastActualResult())
                .addValue("lastPassed", value.lastPassed())
                .addValue("lastRunTime", instant(value.lastRunTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource executionParameters(Execution value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("toolId", value.toolId())
                .addValue("toolVersion", value.toolVersion())
                .addValue("requestJson", json(value.request()))
                .addValue("responseJson", value.response() == null ? null : json(value.response()))
                .addValue("requestHash", value.requestHash())
                .addValue("status", value.status())
                .addValue("mode", value.mode())
                .addValue("approvalToken", value.approvalToken())
                .addValue("confirmedBy", value.confirmedBy())
                .addValue("approvedBy", value.approvedBy())
                .addValue("latencyMs", value.latencyMs())
                .addValue("errorCode", value.errorCode())
                .addValue("errorMessage", value.errorMessage())
                .addValue("traceId", value.traceId())
                .addValue("expireTime", instant(value.expireTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private String executionSelect() {
        return """
                SELECT id, tool_id, tool_version, request_json, response_json, request_hash,
                       status, mode, approval_token, confirmed_by, approved_by, latency_ms,
                       error_code, error_message, trace_id, expire_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_tool_execution_log
                """;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Tool data", exception);
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
            throw new IllegalStateException("Unable to read Tool object", exception);
        }
    }

    private Object object(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Tool response", exception);
        }
    }

    private List<String> stringList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Tool chain", exception);
        }
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
