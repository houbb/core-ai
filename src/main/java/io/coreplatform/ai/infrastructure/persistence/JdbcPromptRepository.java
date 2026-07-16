package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.PromptAbTest;
import io.coreplatform.ai.application.domain.PromptChainStep;
import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.domain.PromptGuardrail;
import io.coreplatform.ai.application.domain.PromptGuardrailPhase;
import io.coreplatform.ai.application.domain.PromptGuardrailType;
import io.coreplatform.ai.application.domain.PromptOutputSchema;
import io.coreplatform.ai.application.domain.PromptRenderLog;
import io.coreplatform.ai.application.domain.PromptSearchCriteria;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptTestCase;
import io.coreplatform.ai.application.domain.PromptVariable;
import io.coreplatform.ai.application.domain.PromptVariableType;
import io.coreplatform.ai.application.domain.PromptVersionData;
import io.coreplatform.ai.application.domain.PromptVisibility;
import io.coreplatform.ai.application.port.PromptRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPromptRepository implements PromptRepository {

    private static final String SELECT_PROMPT = """
            SELECT id, code, name, description, category, scene_id, status,
                   current_version, published_version, visibility, project_code,
                   department_code, owner_user, create_time, update_time,
                   create_user, update_user
              FROM ai_prompt
            """;

    private static final String SELECT_VERSION = """
            SELECT id, prompt_id, version, system_prompt, user_prompt, assistant_prompt,
                   change_log, chain_json, tests_passed, last_tested_time, published_time,
                   create_time, update_time, create_user, update_user
              FROM ai_prompt_version
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcPromptRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_prompt WHERE code = :code",
                Map.of("code", code),
                Integer.class
        );
        return count != null && count > 0;
    }

    @Override
    public PromptData insertPrompt(PromptData prompt) {
        jdbc.update("""
                INSERT INTO ai_prompt(
                    id, code, name, description, category, scene_id, status,
                    current_version, published_version, visibility, project_code,
                    department_code, owner_user,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :category, :sceneId, :status,
                    :currentVersion, :publishedVersion, :visibility, :projectCode,
                    :departmentCode, :ownerUser,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, promptParameters(prompt)
                .addValue("publishedVersion", prompt.publishedVersion()));
        return findById(prompt.id()).orElseThrow();
    }

    @Override
    @Transactional
    public void insertVersion(PromptVersionData version) {
        jdbc.update("""
                INSERT INTO ai_prompt_version(
                    id, prompt_id, version, system_prompt, user_prompt, assistant_prompt,
                    change_log, chain_json, tests_passed, last_tested_time, published_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :promptId, :version, :systemPrompt, :userPrompt, :assistantPrompt,
                    :changeLog, :chainJson, :testsPassed, :lastTestedTime, :publishedTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", version.id())
                .addValue("promptId", version.promptId())
                .addValue("version", version.version())
                .addValue("systemPrompt", version.systemPrompt())
                .addValue("userPrompt", version.userPrompt())
                .addValue("assistantPrompt", version.assistantPrompt())
                .addValue("changeLog", version.changeLog())
                .addValue("chainJson", writeJson(version.chain()))
                .addValue("testsPassed", version.testsPassed())
                .addValue("lastTestedTime", instantValue(version.lastTestedTime()))
                .addValue("publishedTime", instantValue(version.publishedTime()))
                .addValue("createTime", version.createTime().toString())
                .addValue("updateTime", version.updateTime().toString())
                .addValue("createUser", version.createUser())
                .addValue("updateUser", version.updateUser()));

        for (PromptVariable variable : version.variables()) {
            jdbc.update("""
                    INSERT INTO ai_prompt_variable(
                        id, prompt_version_id, name, variable_type, required,
                        default_value, description,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :versionId, :name, :type, :required,
                        :defaultValue, :description,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", variable.id())
                    .addValue("versionId", version.id())
                    .addValue("name", variable.name())
                    .addValue("type", variable.type().name())
                    .addValue("required", variable.required())
                    .addValue("defaultValue", variable.defaultValue())
                    .addValue("description", variable.description())
                    .addValue("createTime", variable.createTime().toString())
                    .addValue("updateTime", variable.updateTime().toString())
                    .addValue("createUser", variable.createUser())
                    .addValue("updateUser", variable.updateUser()));
        }

        if (version.outputSchema().configured()) {
            PromptOutputSchema schema = version.outputSchema();
            jdbc.update("""
                    INSERT INTO ai_prompt_output_schema(
                        id, prompt_version_id, schema_json, strict_mode,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :versionId, :schemaJson, :strictMode,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", schema.id())
                    .addValue("versionId", version.id())
                    .addValue("schemaJson", schema.schemaJson())
                    .addValue("strictMode", schema.strictMode())
                    .addValue("createTime", schema.createTime().toString())
                    .addValue("updateTime", schema.updateTime().toString())
                    .addValue("createUser", schema.createUser())
                    .addValue("updateUser", schema.updateUser()));
        }

        for (PromptGuardrail guardrail : version.guardrails()) {
            jdbc.update("""
                    INSERT INTO ai_prompt_guardrail(
                        id, prompt_version_id, rule_type, phase, config_json, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :versionId, :ruleType, :phase, :configJson, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", guardrail.id())
                    .addValue("versionId", version.id())
                    .addValue("ruleType", guardrail.type().name())
                    .addValue("phase", guardrail.phase().name())
                    .addValue("configJson", guardrail.configJson())
                    .addValue("enabled", guardrail.enabled())
                    .addValue("createTime", guardrail.createTime().toString())
                    .addValue("updateTime", guardrail.updateTime().toString())
                    .addValue("createUser", guardrail.createUser())
                    .addValue("updateUser", guardrail.updateUser()));
        }
    }

    @Override
    public void updatePrompt(
            PromptData prompt,
            PromptStatus status,
            int currentVersion,
            Integer publishedVersion,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_prompt
                   SET name = :name,
                       description = :description,
                       category = :category,
                       scene_id = :sceneId,
                       status = :status,
                       current_version = :currentVersion,
                       published_version = :publishedVersion,
                       visibility = :visibility,
                       project_code = :projectCode,
                       department_code = :departmentCode,
                       owner_user = :ownerUser,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, promptParameters(prompt)
                .addValue("status", status.name())
                .addValue("currentVersion", currentVersion)
                .addValue("publishedVersion", publishedVersion)
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor));
    }

    @Override
    public Optional<PromptData> findById(String id) {
        return jdbc.query(
                SELECT_PROMPT + " WHERE id = :id",
                Map.of("id", id),
                this::mapPrompt
        ).stream().findFirst();
    }

    @Override
    public Optional<PromptData> findByCode(String code) {
        return jdbc.query(
                SELECT_PROMPT + " WHERE code = :code",
                Map.of("code", code),
                this::mapPrompt
        ).stream().findFirst();
    }

    @Override
    public List<PromptData> search(PromptSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(SELECT_PROMPT);
        sql.append(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (criteria != null) {
            if (hasText(criteria.query())) {
                sql.append("""
                         AND (
                            LOWER(name) LIKE :query
                            OR LOWER(code) LIKE :query
                            OR LOWER(COALESCE(description, '')) LIKE :query
                         )
                        """);
                parameters.addValue(
                        "query",
                        "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%"
                );
            }
            if (hasText(criteria.category())) {
                sql.append(" AND category = :category");
                parameters.addValue("category", criteria.category().trim().toUpperCase(Locale.ROOT));
            }
            if (criteria.status() != null) {
                sql.append(" AND status = :status");
                parameters.addValue("status", criteria.status().name());
            }
            if (criteria.visibility() != null) {
                sql.append(" AND visibility = :visibility");
                parameters.addValue("visibility", criteria.visibility().name());
            }
            if (hasText(criteria.sceneId())) {
                sql.append(" AND scene_id = :sceneId");
                parameters.addValue("sceneId", criteria.sceneId().trim());
            }
        }
        sql.append(" ORDER BY LOWER(name), code");
        return jdbc.query(sql.toString(), parameters, this::mapPrompt);
    }

    @Override
    public Optional<PromptVersionData> findVersion(String promptId, int version) {
        return jdbc.query(
                SELECT_VERSION + " WHERE prompt_id = :promptId AND version = :version",
                Map.of("promptId", promptId, "version", version),
                this::mapVersion
        ).stream().findFirst().map(this::attachVersion);
    }

    @Override
    public Optional<PromptVersionData> findVersionById(String versionId) {
        return jdbc.query(
                SELECT_VERSION + " WHERE id = :id",
                Map.of("id", versionId),
                this::mapVersion
        ).stream().findFirst().map(this::attachVersion);
    }

    @Override
    public List<PromptVersionData> findVersions(String promptId) {
        return attachVersions(jdbc.query(
                SELECT_VERSION + " WHERE prompt_id = :promptId ORDER BY version DESC",
                Map.of("promptId", promptId),
                this::mapVersion
        ));
    }

    @Override
    public int maxVersion(String promptId) {
        Integer result = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0)
                  FROM ai_prompt_version
                 WHERE prompt_id = :promptId
                """, Map.of("promptId", promptId), Integer.class);
        return result == null ? 0 : result;
    }

    @Override
    public void markVersionTested(String versionId, boolean passed, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_prompt_version
                   SET tests_passed = :passed,
                       last_tested_time = :testedAt,
                       update_time = :testedAt,
                       update_user = :actor
                 WHERE id = :id
                """, Map.of(
                "id", versionId,
                "passed", passed,
                "testedAt", now.toString(),
                "actor", actor
        ));
    }

    @Override
    public void markVersionPublished(String versionId, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_prompt_version
                   SET published_time = COALESCE(published_time, :publishedAt),
                       update_time = :publishedAt,
                       update_user = :actor
                 WHERE id = :id
                """, Map.of(
                "id", versionId,
                "publishedAt", now.toString(),
                "actor", actor
        ));
    }

    @Override
    public PromptTestCase insertTestCase(PromptTestCase testCase) {
        jdbc.update("""
                INSERT INTO ai_prompt_testcase(
                    id, prompt_version_id, name, input_json, expected_output, enabled,
                    last_actual_output, last_passed, last_run_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :versionId, :name, :inputJson, :expectedOutput, :enabled,
                    :lastActualOutput, :lastPassed, :lastRunTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, testCaseParameters(testCase));
        return findTestCase(testCase.id()).orElseThrow();
    }

    @Override
    public PromptTestCase updateTestCase(PromptTestCase testCase) {
        jdbc.update("""
                UPDATE ai_prompt_testcase
                   SET name = :name,
                       input_json = :inputJson,
                       expected_output = :expectedOutput,
                       enabled = :enabled,
                       last_actual_output = NULL,
                       last_passed = NULL,
                       last_run_time = NULL,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, testCaseParameters(testCase));
        return findTestCase(testCase.id()).orElseThrow();
    }

    @Override
    public void deleteTestCase(String id) {
        jdbc.update("DELETE FROM ai_prompt_testcase WHERE id = :id", Map.of("id", id));
    }

    @Override
    public Optional<PromptTestCase> findTestCase(String id) {
        return jdbc.query("""
                SELECT id, prompt_version_id, name, input_json, expected_output, enabled,
                       last_actual_output, last_passed, last_run_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_testcase
                 WHERE id = :id
                """, Map.of("id", id), this::mapTestCase).stream().findFirst();
    }

    @Override
    public List<PromptTestCase> findTestCases(String versionId) {
        return jdbc.query("""
                SELECT id, prompt_version_id, name, input_json, expected_output, enabled,
                       last_actual_output, last_passed, last_run_time,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_testcase
                 WHERE prompt_version_id = :versionId
                 ORDER BY enabled DESC, LOWER(name), id
                """, Map.of("versionId", versionId), this::mapTestCase);
    }

    @Override
    public void updateTestResult(
            String id,
            String actualOutput,
            boolean passed,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_prompt_testcase
                   SET last_actual_output = :actualOutput,
                       last_passed = :passed,
                       last_run_time = :runAt,
                       update_time = :runAt,
                       update_user = :actor
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("actualOutput", actualOutput)
                .addValue("passed", passed)
                .addValue("runAt", now.toString())
                .addValue("actor", actor));
    }

    @Override
    public void copyTestCases(
            String sourceVersionId,
            String targetVersionId,
            Instant now,
            String actor
    ) {
        for (PromptTestCase source : findTestCases(sourceVersionId)) {
            insertTestCase(new PromptTestCase(
                    UUID.randomUUID().toString(),
                    targetVersionId,
                    source.name(),
                    source.inputJson(),
                    source.expectedOutput(),
                    source.enabled(),
                    null,
                    null,
                    null,
                    now,
                    now,
                    actor,
                    actor
            ));
        }
    }

    @Override
    public PromptAbTest insertAbTest(PromptAbTest abTest) {
        jdbc.update("""
                INSERT INTO ai_prompt_abtest(
                    id, prompt_id, scene_id, name, version_a, version_b, traffic_ratio, enabled,
                    sample_a, sample_b, success_a, success_b,
                    latency_a_total, latency_b_total, cost_a_total, cost_b_total,
                    score_a_total, score_b_total,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :promptId, :sceneId, :name, :versionA, :versionB, :trafficRatio, :enabled,
                    :sampleA, :sampleB, :successA, :successB,
                    :latencyATotal, :latencyBTotal, :costATotal, :costBTotal,
                    :scoreATotal, :scoreBTotal,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, abTestParameters(abTest));
        return findAbTest(abTest.id()).orElseThrow();
    }

    @Override
    public Optional<PromptAbTest> findAbTest(String id) {
        return jdbc.query("""
                SELECT id, prompt_id, scene_id, name, version_a, version_b, traffic_ratio, enabled,
                       sample_a, sample_b, success_a, success_b,
                       latency_a_total, latency_b_total, cost_a_total, cost_b_total,
                       score_a_total, score_b_total,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_abtest
                 WHERE id = :id
                """, Map.of("id", id), this::mapAbTest).stream().findFirst();
    }

    @Override
    public List<PromptAbTest> findAbTests(String promptId) {
        return jdbc.query("""
                SELECT id, prompt_id, scene_id, name, version_a, version_b, traffic_ratio, enabled,
                       sample_a, sample_b, success_a, success_b,
                       latency_a_total, latency_b_total, cost_a_total, cost_b_total,
                       score_a_total, score_b_total,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_abtest
                 WHERE prompt_id = :promptId
                 ORDER BY enabled DESC, update_time DESC
                """, Map.of("promptId", promptId), this::mapAbTest);
    }

    @Override
    public void recordAbObservation(
            String id,
            String variant,
            boolean success,
            long latencyMs,
            BigDecimal cost,
            double score,
            Instant now,
            String actor
    ) {
        String suffix = "A".equals(variant) ? "a" : "b";
        jdbc.update("""
                UPDATE ai_prompt_abtest
                   SET sample_%1$s = sample_%1$s + 1,
                       success_%1$s = success_%1$s + :success,
                       latency_%1$s_total = latency_%1$s_total + :latency,
                       cost_%1$s_total = cost_%1$s_total + :cost,
                       score_%1$s_total = score_%1$s_total + :score,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """.formatted(suffix), new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("success", success ? 1 : 0)
                .addValue("latency", latencyMs)
                .addValue("cost", cost)
                .addValue("score", score)
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor));
    }

    @Override
    public void addRenderLog(PromptRenderLog log, Instant now, String actor) {
        jdbc.update("""
                INSERT INTO ai_prompt_render_log(
                    id, prompt_id, prompt_version_id, variable_names,
                    variables_json, rendered_prompt, content_hash, estimated_tokens,
                    mode, expire_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :promptId, :versionId, :variableNames,
                    :variablesJson, :renderedPrompt, :contentHash, :estimatedTokens,
                    :mode, :expireTime,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", log.id())
                .addValue("promptId", log.promptId())
                .addValue("versionId", log.promptVersionId())
                .addValue("variableNames", log.variableNames())
                .addValue("variablesJson", log.variablesJson())
                .addValue("renderedPrompt", log.renderedPrompt())
                .addValue("contentHash", log.contentHash())
                .addValue("estimatedTokens", log.estimatedTokens())
                .addValue("mode", log.mode())
                .addValue("expireTime", instantValue(log.expireTime()))
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor));
    }

    @Override
    public List<PromptRenderLog> findRenderLogs(String promptId, int limit) {
        return jdbc.query("""
                SELECT id, prompt_id, prompt_version_id, variable_names,
                       variables_json, rendered_prompt, content_hash, estimated_tokens,
                       mode, expire_time, create_time, create_user
                  FROM ai_prompt_render_log
                 WHERE prompt_id = :promptId
                 ORDER BY create_time DESC
                 LIMIT :limit
                """, Map.of("promptId", promptId, "limit", limit), this::mapRenderLog);
    }

    @Override
    public void pruneRenderLogs(String promptId, int keep) {
        List<String> ids = jdbc.queryForList("""
                SELECT id
                  FROM ai_prompt_render_log
                 WHERE prompt_id = :promptId
                 ORDER BY create_time DESC
                """, Map.of("promptId", promptId), String.class);
        if (ids.size() > keep) {
            jdbc.update(
                    "DELETE FROM ai_prompt_render_log WHERE id IN (:ids)",
                    Map.of("ids", ids.subList(keep, ids.size()))
            );
        }
    }

    @Override
    public void pruneExpiredRenderLogs(Instant now) {
        jdbc.update("""
                DELETE FROM ai_prompt_render_log
                 WHERE expire_time IS NOT NULL
                   AND expire_time < :now
                """, Map.of("now", now.toString()));
    }

    @Override
    public void addAudit(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO ai_audit_log(
                    id, resource_type, resource_id, action, result, detail, trace_id,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :resourceType, :resourceId, :action, :result, :detail, :traceId,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("resourceType", entry.resourceType())
                .addValue("resourceId", entry.resourceId())
                .addValue("action", entry.action())
                .addValue("result", entry.result())
                .addValue("detail", entry.detail())
                .addValue("traceId", entry.traceId())
                .addValue("createTime", entry.createTime().toString())
                .addValue("updateTime", entry.createTime().toString())
                .addValue("createUser", entry.createUser())
                .addValue("updateUser", entry.createUser()));
    }

    @Override
    public List<AuditEntry> findAudit(String promptId) {
        return jdbc.query("""
                SELECT id, resource_type, resource_id, action, result, detail,
                       trace_id, create_time, create_user
                  FROM ai_audit_log
                 WHERE resource_type = 'AI_PROMPT'
                   AND resource_id = :promptId
                 ORDER BY create_time DESC
                """, Map.of("promptId", promptId), this::mapAudit);
    }

    private PromptVersionData attachVersion(PromptVersionData version) {
        return new PromptVersionData(
                version.id(),
                version.promptId(),
                version.version(),
                version.systemPrompt(),
                version.userPrompt(),
                version.assistantPrompt(),
                version.changeLog(),
                findVariables(version.id()),
                findSchema(version.id()),
                findGuardrails(version.id()),
                version.chain(),
                version.testsPassed(),
                version.lastTestedTime(),
                version.publishedTime(),
                version.createTime(),
                version.updateTime(),
                version.createUser(),
                version.updateUser()
        );
    }

    private List<PromptVersionData> attachVersions(List<PromptVersionData> versions) {
        if (versions.isEmpty()) {
            return List.of();
        }
        List<String> ids = versions.stream().map(PromptVersionData::id).toList();
        Map<String, List<PromptVariable>> variables = new HashMap<>();
        jdbc.query("""
                SELECT id, prompt_version_id, name, variable_type, required,
                       default_value, description,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_variable
                 WHERE prompt_version_id IN (:versionIds)
                 ORDER BY prompt_version_id, name
                """, Map.of("versionIds", ids), this::mapVariable).forEach(item ->
                variables.computeIfAbsent(
                        item.promptVersionId(),
                        ignored -> new ArrayList<>()
                ).add(item)
        );
        Map<String, PromptOutputSchema> schemas = new HashMap<>();
        jdbc.query("""
                SELECT id, prompt_version_id, schema_json, strict_mode,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_output_schema
                 WHERE prompt_version_id IN (:versionIds)
                """, Map.of("versionIds", ids), this::mapSchema).forEach(item ->
                schemas.put(item.promptVersionId(), item)
        );
        Map<String, List<PromptGuardrail>> guardrails = new HashMap<>();
        jdbc.query("""
                SELECT id, prompt_version_id, rule_type, phase, config_json, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_guardrail
                 WHERE prompt_version_id IN (:versionIds)
                 ORDER BY prompt_version_id, phase, rule_type, id
                """, Map.of("versionIds", ids), this::mapGuardrail).forEach(item ->
                guardrails.computeIfAbsent(
                        item.promptVersionId(),
                        ignored -> new ArrayList<>()
                ).add(item)
        );
        return versions.stream().map(version -> new PromptVersionData(
                version.id(),
                version.promptId(),
                version.version(),
                version.systemPrompt(),
                version.userPrompt(),
                version.assistantPrompt(),
                version.changeLog(),
                variables.getOrDefault(version.id(), List.of()),
                schemas.getOrDefault(version.id(), PromptOutputSchema.empty()),
                guardrails.getOrDefault(version.id(), List.of()),
                version.chain(),
                version.testsPassed(),
                version.lastTestedTime(),
                version.publishedTime(),
                version.createTime(),
                version.updateTime(),
                version.createUser(),
                version.updateUser()
        )).toList();
    }

    private List<PromptVariable> findVariables(String versionId) {
        return jdbc.query("""
                SELECT id, prompt_version_id, name, variable_type, required,
                       default_value, description,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_variable
                 WHERE prompt_version_id = :versionId
                 ORDER BY name
                """, Map.of("versionId", versionId), this::mapVariable);
    }

    private PromptOutputSchema findSchema(String versionId) {
        return jdbc.query("""
                SELECT id, prompt_version_id, schema_json, strict_mode,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_output_schema
                 WHERE prompt_version_id = :versionId
                """, Map.of("versionId", versionId), this::mapSchema)
                .stream()
                .findFirst()
                .orElse(PromptOutputSchema.empty());
    }

    private List<PromptGuardrail> findGuardrails(String versionId) {
        return jdbc.query("""
                SELECT id, prompt_version_id, rule_type, phase, config_json, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_prompt_guardrail
                 WHERE prompt_version_id = :versionId
                 ORDER BY phase, rule_type, id
                """, Map.of("versionId", versionId), this::mapGuardrail);
    }

    private MapSqlParameterSource promptParameters(PromptData prompt) {
        return new MapSqlParameterSource()
                .addValue("id", prompt.id())
                .addValue("code", prompt.code())
                .addValue("name", prompt.name())
                .addValue("description", prompt.description())
                .addValue("category", prompt.category())
                .addValue("sceneId", prompt.sceneId())
                .addValue("status", prompt.status().name())
                .addValue("currentVersion", prompt.currentVersion())
                .addValue("visibility", prompt.visibility().name())
                .addValue("projectCode", prompt.projectCode())
                .addValue("departmentCode", prompt.departmentCode())
                .addValue("ownerUser", prompt.ownerUser())
                .addValue("createTime", prompt.createTime().toString())
                .addValue("updateTime", prompt.updateTime().toString())
                .addValue("createUser", prompt.createUser())
                .addValue("updateUser", prompt.updateUser());
    }

    private MapSqlParameterSource testCaseParameters(PromptTestCase testCase) {
        return new MapSqlParameterSource()
                .addValue("id", testCase.id())
                .addValue("versionId", testCase.promptVersionId())
                .addValue("name", testCase.name())
                .addValue("inputJson", testCase.inputJson())
                .addValue("expectedOutput", testCase.expectedOutput())
                .addValue("enabled", testCase.enabled())
                .addValue("lastActualOutput", testCase.lastActualOutput())
                .addValue("lastPassed", testCase.lastPassed())
                .addValue("lastRunTime", instantValue(testCase.lastRunTime()))
                .addValue("createTime", testCase.createTime().toString())
                .addValue("updateTime", testCase.updateTime().toString())
                .addValue("createUser", testCase.createUser())
                .addValue("updateUser", testCase.updateUser());
    }

    private MapSqlParameterSource abTestParameters(PromptAbTest abTest) {
        return new MapSqlParameterSource()
                .addValue("id", abTest.id())
                .addValue("promptId", abTest.promptId())
                .addValue("sceneId", abTest.sceneId())
                .addValue("name", abTest.name())
                .addValue("versionA", abTest.versionA())
                .addValue("versionB", abTest.versionB())
                .addValue("trafficRatio", abTest.trafficRatio())
                .addValue("enabled", abTest.enabled())
                .addValue("sampleA", abTest.sampleA())
                .addValue("sampleB", abTest.sampleB())
                .addValue("successA", abTest.successA())
                .addValue("successB", abTest.successB())
                .addValue("latencyATotal", abTest.latencyATotal())
                .addValue("latencyBTotal", abTest.latencyBTotal())
                .addValue("costATotal", abTest.costATotal())
                .addValue("costBTotal", abTest.costBTotal())
                .addValue("scoreATotal", abTest.scoreATotal())
                .addValue("scoreBTotal", abTest.scoreBTotal())
                .addValue("createTime", abTest.createTime().toString())
                .addValue("updateTime", abTest.updateTime().toString())
                .addValue("createUser", abTest.createUser())
                .addValue("updateUser", abTest.updateUser());
    }

    private PromptData mapPrompt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptData(
                resultSet.getString("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("category"),
                resultSet.getString("scene_id"),
                PromptStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("current_version"),
                nullableInteger(resultSet, "published_version"),
                PromptVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getString("project_code"),
                resultSet.getString("department_code"),
                resultSet.getString("owner_user"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptVersionData mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptVersionData(
                resultSet.getString("id"),
                resultSet.getString("prompt_id"),
                resultSet.getInt("version"),
                resultSet.getString("system_prompt"),
                resultSet.getString("user_prompt"),
                resultSet.getString("assistant_prompt"),
                resultSet.getString("change_log"),
                List.of(),
                PromptOutputSchema.empty(),
                List.of(),
                readChain(resultSet.getString("chain_json")),
                resultSet.getBoolean("tests_passed"),
                parseInstant(resultSet.getString("last_tested_time")),
                parseInstant(resultSet.getString("published_time")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptVariable mapVariable(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptVariable(
                resultSet.getString("id"),
                resultSet.getString("prompt_version_id"),
                resultSet.getString("name"),
                PromptVariableType.valueOf(resultSet.getString("variable_type")),
                resultSet.getBoolean("required"),
                resultSet.getString("default_value"),
                resultSet.getString("description"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptOutputSchema mapSchema(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptOutputSchema(
                resultSet.getString("id"),
                resultSet.getString("prompt_version_id"),
                resultSet.getString("schema_json"),
                resultSet.getBoolean("strict_mode"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptGuardrail mapGuardrail(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptGuardrail(
                resultSet.getString("id"),
                resultSet.getString("prompt_version_id"),
                PromptGuardrailType.valueOf(resultSet.getString("rule_type")),
                PromptGuardrailPhase.valueOf(resultSet.getString("phase")),
                resultSet.getString("config_json"),
                resultSet.getBoolean("enabled"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptTestCase mapTestCase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptTestCase(
                resultSet.getString("id"),
                resultSet.getString("prompt_version_id"),
                resultSet.getString("name"),
                resultSet.getString("input_json"),
                resultSet.getString("expected_output"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("last_actual_output"),
                nullableBoolean(resultSet, "last_passed"),
                parseInstant(resultSet.getString("last_run_time")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptAbTest mapAbTest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptAbTest(
                resultSet.getString("id"),
                resultSet.getString("prompt_id"),
                resultSet.getString("scene_id"),
                resultSet.getString("name"),
                resultSet.getInt("version_a"),
                resultSet.getInt("version_b"),
                resultSet.getInt("traffic_ratio"),
                resultSet.getBoolean("enabled"),
                resultSet.getLong("sample_a"),
                resultSet.getLong("sample_b"),
                resultSet.getLong("success_a"),
                resultSet.getLong("success_b"),
                resultSet.getLong("latency_a_total"),
                resultSet.getLong("latency_b_total"),
                resultSet.getBigDecimal("cost_a_total"),
                resultSet.getBigDecimal("cost_b_total"),
                resultSet.getDouble("score_a_total"),
                resultSet.getDouble("score_b_total"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private PromptRenderLog mapRenderLog(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptRenderLog(
                resultSet.getString("id"),
                resultSet.getString("prompt_id"),
                resultSet.getString("prompt_version_id"),
                resultSet.getString("variable_names"),
                resultSet.getString("variables_json"),
                resultSet.getString("rendered_prompt"),
                resultSet.getString("content_hash"),
                resultSet.getInt("estimated_tokens"),
                resultSet.getString("mode"),
                parseInstant(resultSet.getString("expire_time")),
                Instant.parse(resultSet.getString("create_time")),
                resultSet.getString("create_user")
        );
    }

    private AuditEntry mapAudit(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AuditEntry(
                resultSet.getString("id"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("action"),
                resultSet.getString("result"),
                resultSet.getString("detail"),
                resultSet.getString("trace_id"),
                Instant.parse(resultSet.getString("create_time")),
                resultSet.getString("create_user")
        );
    }

    private List<PromptChainStep> readChain(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(PromptChainStep.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Prompt chain", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Prompt data", exception);
        }
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private String instantValue(Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant parseInstant(String value) {
        return hasText(value) ? Instant.parse(value) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
