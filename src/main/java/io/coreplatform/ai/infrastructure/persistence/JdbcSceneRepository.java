package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.SceneConfiguration;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneSearchCriteria;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.domain.SceneTemplate;
import io.coreplatform.ai.application.domain.SceneVersion;
import io.coreplatform.ai.application.domain.SceneWorkflowStep;
import io.coreplatform.ai.application.port.SceneRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
public class JdbcSceneRepository implements SceneRepository {

    private static final String SELECT_SCENE = """
            SELECT id,
                   code,
                   name,
                   description,
                   category,
                   icon,
                   status,
                   enabled,
                   version,
                   recommended,
                   workflow_json,
                   last_tested_at,
                   last_tested_version,
                   create_time,
                   update_time,
                   create_user,
                   update_user
              FROM ai_scene
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSceneRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_scene WHERE code = :code",
                Map.of("code", code),
                Integer.class
        );
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public SceneData insert(SceneData scene, Instant now, String actor) {
        MapSqlParameterSource values = sceneParameters(scene, now, actor);
        jdbc.update("""
                INSERT INTO ai_scene(
                    id, code, name, description, category, icon, status, enabled, version,
                    recommended, workflow_json, last_tested_at, last_tested_version,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :category, :icon, :status, :enabled, :version,
                    :recommended, :workflowJson, :lastTestedAt, :lastTestedVersion,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, values);
        replaceRelations(scene, now, actor);
        return findById(scene.id()).orElseThrow();
    }

    @Override
    @Transactional
    public SceneData update(SceneData scene, Instant now, String actor) {
        MapSqlParameterSource values = sceneParameters(scene, now, actor);
        jdbc.update("""
                UPDATE ai_scene
                   SET name = :name,
                       description = :description,
                       category = :category,
                       icon = :icon,
                       recommended = :recommended,
                       workflow_json = :workflowJson,
                       last_tested_at = NULL,
                       last_tested_version = NULL,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, values);
        replaceRelations(scene, now, actor);
        return findById(scene.id()).orElseThrow();
    }

    @Override
    public Optional<SceneData> findById(String id) {
        return jdbc.query(
                SELECT_SCENE + " WHERE id = :id",
                Map.of("id", id),
                this::mapScene
        ).stream().findFirst().map(this::attachRelations);
    }

    @Override
    public Optional<SceneData> findByCode(String code) {
        return jdbc.query(
                SELECT_SCENE + " WHERE code = :code",
                Map.of("code", code),
                this::mapScene
        ).stream().findFirst().map(this::attachRelations);
    }

    @Override
    public List<SceneData> search(SceneSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(SELECT_SCENE);
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
                parameters.addValue("query", "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%");
            }
            if (hasText(criteria.category())) {
                sql.append(" AND category = :category");
                parameters.addValue("category", criteria.category().trim().toUpperCase(Locale.ROOT));
            }
            if (criteria.status() != null) {
                sql.append(" AND status = :status");
                parameters.addValue("status", criteria.status().name());
            }
            if (criteria.enabled() != null) {
                sql.append(" AND enabled = :enabled");
                parameters.addValue("enabled", criteria.enabled());
            }
            if (criteria.recommended() != null) {
                sql.append(" AND recommended = :recommended");
                parameters.addValue("recommended", criteria.recommended());
            }
        }
        sql.append("""
                 ORDER BY recommended DESC,
                          enabled DESC,
                          LOWER(name),
                          code
                """);
        return attachRelations(jdbc.query(sql.toString(), parameters, this::mapScene));
    }

    @Override
    public void updateLifecycle(
            String id,
            SceneStatus status,
            boolean enabled,
            int version,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_scene
                   SET status = :status,
                       enabled = :enabled,
                       version = :version,
                       last_tested_at = CASE WHEN :status = 'DRAFT' THEN NULL ELSE last_tested_at END,
                       last_tested_version = CASE WHEN :status = 'DRAFT' THEN NULL ELSE last_tested_version END,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, Map.of(
                "id", id,
                "status", status.name(),
                "enabled", enabled,
                "version", version,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
    }

    @Override
    public void markTested(String id, int version, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_scene
                   SET last_tested_at = :testedAt,
                       last_tested_version = :version,
                       update_time = :testedAt,
                       update_user = :actor
                 WHERE id = :id
                """, Map.of(
                "id", id,
                "version", version,
                "testedAt", now.toString(),
                "actor", actor
        ));
    }

    @Override
    public boolean versionExists(String sceneId, int version) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM ai_scene_version
                 WHERE scene_id = :sceneId
                   AND version = :version
                """, Map.of("sceneId", sceneId, "version", version), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void addVersion(SceneVersion version, Instant now, String actor) {
        jdbc.update("""
                INSERT INTO ai_scene_version(
                    id, scene_id, version, config_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :sceneId, :version, :configJson,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", version.id())
                .addValue("sceneId", version.sceneId())
                .addValue("version", version.version())
                .addValue("configJson", writeJson(version.configuration()))
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor));
    }

    @Override
    public List<SceneVersion> findVersions(String sceneId) {
        return jdbc.query("""
                SELECT id, scene_id, version, config_json, create_time, create_user
                  FROM ai_scene_version
                 WHERE scene_id = :sceneId
                 ORDER BY version DESC
                """, Map.of("sceneId", sceneId), this::mapVersion);
    }

    @Override
    public Optional<SceneVersion> findVersion(String sceneId, int version) {
        return jdbc.query("""
                SELECT id, scene_id, version, config_json, create_time, create_user
                  FROM ai_scene_version
                 WHERE scene_id = :sceneId
                   AND version = :version
                """, Map.of("sceneId", sceneId, "version", version), this::mapVersion)
                .stream()
                .findFirst();
    }

    @Override
    public int maxVersion(String sceneId) {
        Integer version = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0)
                  FROM ai_scene_version
                 WHERE scene_id = :sceneId
                """, Map.of("sceneId", sceneId), Integer.class);
        return version == null ? 0 : version;
    }

    @Override
    public List<SceneTemplate> findTemplates() {
        return jdbc.query("""
                SELECT id,
                       default_code,
                       template_name,
                       description,
                       category,
                       icon,
                       builtin,
                       recommended,
                       config_json,
                       create_time,
                       update_time,
                       create_user,
                       update_user
                  FROM ai_scene_template
                 ORDER BY builtin DESC, recommended DESC, LOWER(template_name)
                """, this::mapTemplate);
    }

    @Override
    public Optional<SceneTemplate> findTemplateById(String id) {
        return jdbc.query("""
                SELECT id,
                       default_code,
                       template_name,
                       description,
                       category,
                       icon,
                       builtin,
                       recommended,
                       config_json,
                       create_time,
                       update_time,
                       create_user,
                       update_user
                  FROM ai_scene_template
                 WHERE id = :id
                """, Map.of("id", id), this::mapTemplate).stream().findFirst();
    }

    @Override
    public SceneTemplate saveTemplate(SceneTemplate template, Instant now, String actor) {
        jdbc.update("""
                INSERT INTO ai_scene_template(
                    id, default_code, template_name, description, category, icon,
                    builtin, recommended, config_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :defaultCode, :templateName, :description, :category, :icon,
                    :builtin, :recommended, :configJson,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", template.id())
                .addValue("defaultCode", template.defaultCode())
                .addValue("templateName", template.templateName())
                .addValue("description", template.description())
                .addValue("category", template.category())
                .addValue("icon", template.icon())
                .addValue("builtin", template.builtin())
                .addValue("recommended", template.recommended())
                .addValue("configJson", writeJson(template.configuration()))
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor));
        return findTemplateById(template.id()).orElseThrow();
    }

    @Override
    public void deleteTemplate(String id) {
        jdbc.update(
                "DELETE FROM ai_scene_template WHERE id = :id AND builtin = FALSE",
                Map.of("id", id)
        );
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
    public List<AuditEntry> findAudit(String sceneId) {
        return jdbc.query("""
                SELECT id,
                       resource_type,
                       resource_id,
                       action,
                       result,
                       detail,
                       trace_id,
                       create_time,
                       create_user
                  FROM ai_audit_log
                 WHERE resource_type = 'AI_SCENE'
                   AND resource_id = :sceneId
                 ORDER BY create_time DESC
                """, Map.of("sceneId", sceneId), this::mapAudit);
    }

    private MapSqlParameterSource sceneParameters(SceneData scene, Instant now, String actor) {
        return new MapSqlParameterSource()
                .addValue("id", scene.id())
                .addValue("code", scene.code())
                .addValue("name", scene.name())
                .addValue("description", scene.description())
                .addValue("category", scene.category())
                .addValue("icon", scene.icon())
                .addValue("status", scene.status().name())
                .addValue("enabled", scene.enabled())
                .addValue("version", scene.version())
                .addValue("recommended", scene.recommended())
                .addValue("workflowJson", writeJson(scene.workflow()))
                .addValue("lastTestedAt", instantValue(scene.lastTestedAt()))
                .addValue("lastTestedVersion", scene.lastTestedVersion())
                .addValue("createTime", scene.createTime().toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", scene.createUser())
                .addValue("updateUser", actor);
    }

    private void replaceRelations(SceneData scene, Instant now, String actor) {
        Map<String, String> sceneId = Map.of("sceneId", scene.id());
        jdbc.update("DELETE FROM ai_scene_model WHERE scene_id = :sceneId", sceneId);
        jdbc.update("DELETE FROM ai_scene_parameter WHERE scene_id = :sceneId", sceneId);
        jdbc.update("DELETE FROM ai_scene_prompt WHERE scene_id = :sceneId", sceneId);
        jdbc.update("DELETE FROM ai_scene_permission WHERE scene_id = :sceneId", sceneId);

        for (SceneModelBinding binding : scene.models()) {
            jdbc.update("""
                    INSERT INTO ai_scene_model(
                        id, scene_id, model_alias, priority, fallback, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :sceneId, :modelAlias, :priority, :fallback, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", binding.id())
                    .addValue("sceneId", scene.id())
                    .addValue("modelAlias", binding.modelAlias())
                    .addValue("priority", binding.priority())
                    .addValue("fallback", binding.fallback())
                    .addValue("enabled", binding.enabled())
                    .addValue("createTime", now.toString())
                    .addValue("updateTime", now.toString())
                    .addValue("createUser", actor)
                    .addValue("updateUser", actor));
        }

        SceneParameters parameters = scene.parameters();
        jdbc.update("""
                INSERT INTO ai_scene_parameter(
                    id, scene_id, temperature, top_p, max_output_tokens,
                    reasoning_effort, json_mode, streaming,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :sceneId, :temperature, :topP, :maxOutputTokens,
                    :reasoningEffort, :jsonMode, :streaming,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("sceneId", scene.id())
                .addValue("temperature", parameters.temperature())
                .addValue("topP", parameters.topP())
                .addValue("maxOutputTokens", parameters.maxOutputTokens())
                .addValue("reasoningEffort", parameters.reasoningEffort())
                .addValue("jsonMode", parameters.jsonMode())
                .addValue("streaming", parameters.streaming())
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor));

        ScenePromptBinding prompt = scene.prompt();
        jdbc.update("""
                INSERT INTO ai_scene_prompt(
                    id, scene_id, prompt_id, prompt_version,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :sceneId, :promptId, :promptVersion,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("sceneId", scene.id())
                .addValue("promptId", prompt.promptId())
                .addValue("promptVersion", prompt.promptVersion())
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor));

        for (ScenePermission permission : scene.permissions()) {
            jdbc.update("""
                    INSERT INTO ai_scene_permission(
                        id, scene_id, permission_type, permission_value,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :sceneId, :permissionType, :permissionValue,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", permission.id())
                    .addValue("sceneId", scene.id())
                    .addValue("permissionType", permission.type().name())
                    .addValue("permissionValue", permission.value())
                    .addValue("createTime", now.toString())
                    .addValue("updateTime", now.toString())
                    .addValue("createUser", actor)
                    .addValue("updateUser", actor));
        }
    }

    private SceneData attachRelations(SceneData scene) {
        return new SceneData(
                scene.id(),
                scene.code(),
                scene.name(),
                scene.description(),
                scene.category(),
                scene.icon(),
                scene.status(),
                scene.enabled(),
                scene.version(),
                scene.recommended(),
                scene.lastTestedAt(),
                scene.lastTestedVersion(),
                findModels(scene.id()),
                findParameters(scene.id()),
                findPrompt(scene.id()),
                findPermissions(scene.id()),
                scene.workflow(),
                scene.createTime(),
                scene.updateTime(),
                scene.createUser(),
                scene.updateUser()
        );
    }

    private List<SceneData> attachRelations(List<SceneData> scenes) {
        if (scenes.isEmpty()) {
            return List.of();
        }
        List<String> ids = scenes.stream().map(SceneData::id).toList();
        Map<String, List<SceneModelBinding>> models = new HashMap<>();
        jdbc.query("""
                SELECT id, scene_id, model_alias, priority, fallback, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_scene_model
                 WHERE scene_id IN (:sceneIds)
                 ORDER BY scene_id, priority, model_alias
                """, Map.of("sceneIds", ids), this::mapModelBinding).forEach(binding ->
                models.computeIfAbsent(binding.sceneId(), ignored -> new ArrayList<>()).add(binding)
        );

        Map<String, SceneParameters> parameters = new HashMap<>();
        jdbc.query("""
                SELECT scene_id, temperature, top_p, max_output_tokens,
                       reasoning_effort, json_mode, streaming
                  FROM ai_scene_parameter
                 WHERE scene_id IN (:sceneIds)
                """, Map.of("sceneIds", ids), this::mapParameterRecord).forEach(record ->
                parameters.put(record.sceneId(), record.parameters())
        );

        Map<String, ScenePromptBinding> prompts = new HashMap<>();
        jdbc.query("""
                SELECT scene_id, prompt_id, prompt_version
                  FROM ai_scene_prompt
                 WHERE scene_id IN (:sceneIds)
                """, Map.of("sceneIds", ids), this::mapPromptRecord).forEach(record ->
                prompts.put(record.sceneId(), record.prompt())
        );

        Map<String, List<ScenePermission>> permissions = new HashMap<>();
        jdbc.query("""
                SELECT id, scene_id, permission_type, permission_value,
                       create_time, update_time, create_user, update_user
                  FROM ai_scene_permission
                 WHERE scene_id IN (:sceneIds)
                 ORDER BY scene_id, permission_type, permission_value
                """, Map.of("sceneIds", ids), this::mapPermission).forEach(permission ->
                permissions.computeIfAbsent(permission.sceneId(), ignored -> new ArrayList<>()).add(permission)
        );

        return scenes.stream()
                .map(scene -> new SceneData(
                        scene.id(),
                        scene.code(),
                        scene.name(),
                        scene.description(),
                        scene.category(),
                        scene.icon(),
                        scene.status(),
                        scene.enabled(),
                        scene.version(),
                        scene.recommended(),
                        scene.lastTestedAt(),
                        scene.lastTestedVersion(),
                        models.getOrDefault(scene.id(), List.of()),
                        parameters.getOrDefault(scene.id(), SceneParameters.defaults()),
                        prompts.getOrDefault(scene.id(), ScenePromptBinding.empty()),
                        permissions.getOrDefault(scene.id(), List.of()),
                        scene.workflow(),
                        scene.createTime(),
                        scene.updateTime(),
                        scene.createUser(),
                        scene.updateUser()
                ))
                .toList();
    }

    private List<SceneModelBinding> findModels(String sceneId) {
        return jdbc.query("""
                SELECT id, scene_id, model_alias, priority, fallback, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_scene_model
                 WHERE scene_id = :sceneId
                 ORDER BY priority, model_alias
                """, Map.of("sceneId", sceneId), this::mapModelBinding);
    }

    private SceneParameters findParameters(String sceneId) {
        return jdbc.query("""
                SELECT scene_id, temperature, top_p, max_output_tokens,
                       reasoning_effort, json_mode, streaming
                  FROM ai_scene_parameter
                 WHERE scene_id = :sceneId
                """, Map.of("sceneId", sceneId), this::mapParameterRecord)
                .stream()
                .findFirst()
                .map(ParameterRecord::parameters)
                .orElse(SceneParameters.defaults());
    }

    private ScenePromptBinding findPrompt(String sceneId) {
        return jdbc.query("""
                SELECT scene_id, prompt_id, prompt_version
                  FROM ai_scene_prompt
                 WHERE scene_id = :sceneId
                """, Map.of("sceneId", sceneId), this::mapPromptRecord)
                .stream()
                .findFirst()
                .map(PromptRecord::prompt)
                .orElse(ScenePromptBinding.empty());
    }

    private List<ScenePermission> findPermissions(String sceneId) {
        return jdbc.query("""
                SELECT id, scene_id, permission_type, permission_value,
                       create_time, update_time, create_user, update_user
                  FROM ai_scene_permission
                 WHERE scene_id = :sceneId
                 ORDER BY permission_type, permission_value
                """, Map.of("sceneId", sceneId), this::mapPermission);
    }

    private SceneData mapScene(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SceneData(
                resultSet.getString("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("category"),
                resultSet.getString("icon"),
                SceneStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("enabled"),
                resultSet.getInt("version"),
                resultSet.getBoolean("recommended"),
                parseInstant(resultSet.getString("last_tested_at")),
                nullableInteger(resultSet, "last_tested_version"),
                List.of(),
                SceneParameters.defaults(),
                ScenePromptBinding.empty(),
                List.of(),
                readWorkflow(resultSet.getString("workflow_json")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private SceneModelBinding mapModelBinding(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SceneModelBinding(
                resultSet.getString("id"),
                resultSet.getString("scene_id"),
                resultSet.getString("model_alias"),
                resultSet.getInt("priority"),
                resultSet.getBoolean("fallback"),
                resultSet.getBoolean("enabled"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private ParameterRecord mapParameterRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ParameterRecord(
                resultSet.getString("scene_id"),
                new SceneParameters(
                        nullableDouble(resultSet, "temperature"),
                        nullableDouble(resultSet, "top_p"),
                        nullableInteger(resultSet, "max_output_tokens"),
                        resultSet.getString("reasoning_effort"),
                        resultSet.getBoolean("json_mode"),
                        resultSet.getBoolean("streaming")
                )
        );
    }

    private PromptRecord mapPromptRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptRecord(
                resultSet.getString("scene_id"),
                new ScenePromptBinding(
                        resultSet.getString("prompt_id"),
                        nullableInteger(resultSet, "prompt_version")
                )
        );
    }

    private ScenePermission mapPermission(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ScenePermission(
                resultSet.getString("id"),
                resultSet.getString("scene_id"),
                ScenePermissionType.valueOf(resultSet.getString("permission_type")),
                resultSet.getString("permission_value"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private SceneVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SceneVersion(
                resultSet.getString("id"),
                resultSet.getString("scene_id"),
                resultSet.getInt("version"),
                readConfiguration(resultSet.getString("config_json")),
                Instant.parse(resultSet.getString("create_time")),
                resultSet.getString("create_user")
        );
    }

    private SceneTemplate mapTemplate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SceneTemplate(
                resultSet.getString("id"),
                resultSet.getString("default_code"),
                resultSet.getString("template_name"),
                resultSet.getString("description"),
                resultSet.getString("category"),
                resultSet.getString("icon"),
                resultSet.getBoolean("builtin"),
                resultSet.getBoolean("recommended"),
                readConfiguration(resultSet.getString("config_json")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
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

    private List<SceneWorkflowStep> readWorkflow(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(SceneWorkflowStep.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Scene workflow", exception);
        }
    }

    private SceneConfiguration readConfiguration(String json) {
        try {
            return objectMapper.readValue(json, SceneConfiguration.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Scene configuration", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Scene configuration", exception);
        }
    }

    private String instantValue(Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant parseInstant(String value) {
        return hasText(value) ? Instant.parse(value) : null;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ParameterRecord(String sceneId, SceneParameters parameters) {
    }

    private record PromptRecord(String sceneId, ScenePromptBinding prompt) {
    }
}
