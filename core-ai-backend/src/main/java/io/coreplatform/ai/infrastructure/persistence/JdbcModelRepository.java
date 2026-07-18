package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ModelAlias;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelSearchCriteria;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.port.ModelDiscoveryPort;
import io.coreplatform.ai.application.port.ModelRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcModelRepository implements ModelRepository, ModelDiscoveryPort {

    private static final TypeReference<Map<Capability, Boolean>> CAPABILITY_OVERRIDES =
            new TypeReference<>() {
            };

    private static final String SELECT_MODEL = """
            SELECT m.id,
                   m.provider_id,
                   p.code AS provider_code,
                   p.name AS provider_name,
                   p.enabled AS provider_enabled,
                   h.latency_ms AS provider_latency_ms,
                   m.remote_model_id,
                   m.display_name,
                   m.category,
                   m.description,
                   m.status,
                   m.enabled,
                   m.available_from_provider,
                   m.recommended,
                   m.favorite,
                   m.max_context_tokens,
                   m.max_input_tokens,
                   m.max_output_tokens,
                   m.default_max_tokens,
                   m.context_manually_overridden,
                   m.last_discovered_at,
                   m.create_time,
                   m.update_time,
                   m.create_user,
                   m.update_user,
                   c.chat,
                   c.reasoning,
                   c.vision,
                   c.embedding,
                   c.rerank,
                   c.image,
                   c.video,
                   c.audio,
                   c.speech,
                   c.moderation,
                   c.ocr,
                   c.tool_call,
                   c.json_mode,
                   c.streaming,
                   c.manual_override_json,
                   parameter.temperature,
                   parameter.top_p,
                   parameter.frequency_penalty,
                   parameter.presence_penalty,
                   parameter.max_output_tokens AS parameter_max_output_tokens,
                   parameter.reasoning_effort,
                   parameter.seed
              FROM ai_model m
              JOIN ai_provider p ON p.id = m.provider_id
              LEFT JOIN ai_provider_health h ON h.provider_id = p.id
              JOIN ai_model_capability c ON c.model_id = m.id
              JOIN ai_model_parameter parameter ON parameter.model_id = m.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<ModelData> modelRowMapper = this::mapModel;

    public JdbcModelRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ModelData> findById(String id) {
        List<ModelData> models = jdbc.query(
                SELECT_MODEL + " WHERE m.id = :id AND m.deleted = FALSE AND p.deleted = FALSE",
                Map.of("id", id),
                modelRowMapper
        );
        return models.stream().findFirst().map(this::attachRelations);
    }

    @Override
    public List<ModelData> search(ModelSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(SELECT_MODEL);
        sql.append(" WHERE m.deleted = FALSE AND p.deleted = FALSE");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (criteria != null) {
            if (hasText(criteria.query())) {
                sql.append("""
                         AND (
                            LOWER(m.display_name) LIKE :query
                            OR LOWER(m.remote_model_id) LIKE :query
                            OR LOWER(COALESCE(m.description, '')) LIKE :query
                            OR LOWER(p.name) LIKE :query
                            OR EXISTS (
                                SELECT 1
                                  FROM ai_model_tag search_tag
                                 WHERE search_tag.model_id = m.id
                                   AND LOWER(search_tag.tag) LIKE :query
                            )
                         )
                        """);
                parameters.addValue("query", "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%");
            }
            if (hasText(criteria.providerId())) {
                sql.append(" AND m.provider_id = :providerId");
                parameters.addValue("providerId", criteria.providerId());
            }
            if (criteria.category() != null) {
                sql.append(" AND m.category = :category");
                parameters.addValue("category", criteria.category().name());
            }
            if (criteria.capability() != null) {
                sql.append(" AND c.").append(capabilityColumn(criteria.capability())).append(" = TRUE");
            }
            if (criteria.status() != null) {
                sql.append(" AND m.status = :status");
                parameters.addValue("status", criteria.status().name());
            }
            if (criteria.enabled() != null) {
                sql.append(" AND m.enabled = :enabled");
                parameters.addValue("enabled", criteria.enabled());
            }
            if (criteria.favorite() != null) {
                sql.append(" AND m.favorite = :favorite");
                parameters.addValue("favorite", criteria.favorite());
            }
            if (criteria.recommended() != null) {
                sql.append(" AND m.recommended = :recommended");
                parameters.addValue("recommended", criteria.recommended());
            }
            if (criteria.available() != null) {
                sql.append(" AND m.available_from_provider = :available");
                parameters.addValue("available", criteria.available());
            }
            if (criteria.minimumContextTokens() != null) {
                sql.append(" AND COALESCE(m.max_context_tokens, 0) >= :minimumContext");
                parameters.addValue("minimumContext", criteria.minimumContextTokens());
            }
            if (hasText(criteria.tag())) {
                sql.append("""
                         AND EXISTS (
                            SELECT 1
                              FROM ai_model_tag exact_tag
                             WHERE exact_tag.model_id = m.id
                               AND LOWER(exact_tag.tag) = :tag
                         )
                        """);
                parameters.addValue("tag", criteria.tag().trim().toLowerCase(Locale.ROOT));
            }
        }
        sql.append("""
                 ORDER BY m.recommended DESC,
                          m.favorite DESC,
                          m.enabled DESC,
                          LOWER(m.display_name) ASC
                """);
        return attachRelations(jdbc.query(sql.toString(), parameters, modelRowMapper));
    }

    @Override
    @Transactional
    public ModelData update(ModelData model, Instant now, String actor) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", model.id())
                .addValue("displayName", model.displayName())
                .addValue("category", model.category().name())
                .addValue("description", model.description())
                .addValue("maxContextTokens", model.maxContextTokens())
                .addValue("maxInputTokens", model.maxInputTokens())
                .addValue("maxOutputTokens", model.maxOutputTokens())
                .addValue("defaultMaxTokens", model.defaultMaxTokens())
                .addValue("contextManuallyOverridden", model.contextManuallyOverridden())
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
        jdbc.update("""
                UPDATE ai_model
                   SET display_name = :displayName,
                       category = :category,
                       description = :description,
                       max_context_tokens = :maxContextTokens,
                       max_input_tokens = :maxInputTokens,
                       max_output_tokens = :maxOutputTokens,
                       default_max_tokens = :defaultMaxTokens,
                       context_manually_overridden = :contextManuallyOverridden,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                   AND deleted = FALSE
                """, parameters);
        replaceTags(model.id(), model.tags(), now, actor);
        return findById(model.id()).orElseThrow();
    }

    @Override
    public void updateStatus(String id, ModelStatus status, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_model
                   SET status = :status,
                       enabled = :enabled,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                   AND deleted = FALSE
                """, Map.of(
                "id", id,
                "status", status.name(),
                "enabled", status == ModelStatus.ENABLED,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
    }

    @Override
    public void updateCapabilities(
            String id,
            Set<Capability> capabilities,
            Map<Capability, Boolean> manualOverrides,
            Instant now,
            String actor
    ) {
        MapSqlParameterSource parameters = capabilityParameters(
                id,
                capabilities,
                manualOverrides,
                now,
                actor
        );
        jdbc.update("""
                UPDATE ai_model_capability
                   SET chat = :chat,
                       reasoning = :reasoning,
                       vision = :vision,
                       embedding = :embedding,
                       rerank = :rerank,
                       image = :image,
                       video = :video,
                       audio = :audio,
                       speech = :speech,
                       moderation = :moderation,
                       ocr = :ocr,
                       tool_call = :toolCall,
                       json_mode = :jsonMode,
                       streaming = :streaming,
                       manual_override_json = :manualOverrides,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE model_id = :id
                """, parameters);
    }

    @Override
    public void updateParameters(String id, ModelParameters parameters, Instant now, String actor) {
        MapSqlParameterSource values = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("temperature", parameters.temperature())
                .addValue("topP", parameters.topP())
                .addValue("frequencyPenalty", parameters.frequencyPenalty())
                .addValue("presencePenalty", parameters.presencePenalty())
                .addValue("maxOutputTokens", parameters.maxOutputTokens())
                .addValue("reasoningEffort", parameters.reasoningEffort())
                .addValue("seed", parameters.seed())
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
        jdbc.update("""
                UPDATE ai_model_parameter
                   SET temperature = :temperature,
                       top_p = :topP,
                       frequency_penalty = :frequencyPenalty,
                       presence_penalty = :presencePenalty,
                       max_output_tokens = :maxOutputTokens,
                       reasoning_effort = :reasoningEffort,
                       seed = :seed,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE model_id = :id
                """, values);
    }

    @Override
    public ModelPricing addPricing(ModelPricing pricing, Instant now, String actor) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", pricing.id())
                .addValue("modelId", pricing.modelId())
                .addValue("currency", pricing.currency())
                .addValue("promptPrice", pricing.promptPrice())
                .addValue("completionPrice", pricing.completionPrice())
                .addValue("cacheReadPrice", pricing.cacheReadPrice())
                .addValue("cacheWritePrice", pricing.cacheWritePrice())
                .addValue("effectiveTime", pricing.effectiveTime().toString())
                .addValue("source", pricing.source())
                .addValue("notes", pricing.notes())
                .addValue("createTime", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", actor)
                .addValue("updateUser", actor);
        jdbc.update("""
                INSERT INTO ai_model_pricing(
                    id, model_id, currency, prompt_price, completion_price,
                    cache_read_price, cache_write_price, effective_time, source, notes,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :modelId, :currency, :promptPrice, :completionPrice,
                    :cacheReadPrice, :cacheWritePrice, :effectiveTime, :source, :notes,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, parameters);
        return findPricing(pricing.modelId()).stream()
                .filter(item -> item.id().equals(pricing.id()))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<ModelPricing> findPricing(String modelId) {
        return jdbc.query("""
                SELECT id,
                       model_id,
                       currency,
                       prompt_price,
                       completion_price,
                       cache_read_price,
                       cache_write_price,
                       effective_time,
                       source,
                       notes,
                       create_time,
                       create_user
                  FROM ai_model_pricing
                 WHERE model_id = :modelId
                 ORDER BY effective_time DESC
                """, Map.of("modelId", modelId), this::mapPricing);
    }

    @Override
    public void setFlags(
            String id,
            boolean favorite,
            boolean recommended,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_model
                   SET favorite = :favorite,
                       recommended = :recommended,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                   AND deleted = FALSE
                """, Map.of(
                "id", id,
                "favorite", favorite,
                "recommended", recommended,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
    }

    @Override
    public void softDelete(String id, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_model
                   SET status = 'DELETED',
                       enabled = FALSE,
                       deleted = TRUE,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, Map.of(
                "id", id,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
        jdbc.update(
                """
                UPDATE ai_model_alias
                   SET enabled = FALSE,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE model_id = :id
                """,
                Map.of(
                        "id", id,
                        "updateTime", now.toString(),
                        "updateUser", actor
                )
        );
    }

    @Override
    public boolean aliasExists(String alias, String modelId, String excludedId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM ai_model_alias
                 WHERE alias_code = :alias
                   AND model_id = :modelId
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("alias", alias)
                .addValue("modelId", modelId);
        if (excludedId != null) {
            sql.append(" AND id <> :excludedId");
            parameters.addValue("excludedId", excludedId);
        }
        Integer count = jdbc.queryForObject(sql.toString(), parameters, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public ModelAlias saveAlias(ModelAlias alias, Instant now, String actor) {
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_model_alias WHERE id = :id",
                Map.of("id", alias.id()),
                Integer.class
        ) > 0;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", alias.id())
                .addValue("alias", alias.alias())
                .addValue("modelId", alias.modelId())
                .addValue("scene", alias.scene())
                .addValue("priority", alias.priority())
                .addValue("enabled", alias.enabled())
                .addValue("createTime", alias.createTime().toString())
                .addValue("updateTime", now.toString())
                .addValue("createUser", alias.createUser())
                .addValue("updateUser", actor);
        if (exists) {
            jdbc.update("""
                    UPDATE ai_model_alias
                       SET alias_code = :alias,
                           model_id = :modelId,
                           scene = :scene,
                           priority = :priority,
                           enabled = :enabled,
                           update_time = :updateTime,
                           update_user = :updateUser
                     WHERE id = :id
                    """, parameters);
        } else {
            jdbc.update("""
                    INSERT INTO ai_model_alias(
                        id, alias_code, model_id, scene, priority, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :alias, :modelId, :scene, :priority, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
        return findAliasById(alias.id()).orElseThrow();
    }

    @Override
    public void deleteAlias(String id) {
        jdbc.update("DELETE FROM ai_model_alias WHERE id = :id", Map.of("id", id));
    }

    @Override
    public Optional<ModelAlias> findAliasById(String id) {
        return jdbc.query("""
                SELECT a.id,
                       a.alias_code,
                       a.model_id,
                       m.display_name AS model_display_name,
                       p.name AS provider_name,
                       a.scene,
                       a.priority,
                       a.enabled,
                       a.create_time,
                       a.update_time,
                       a.create_user,
                       a.update_user
                  FROM ai_model_alias a
                  JOIN ai_model m ON m.id = a.model_id
                  JOIN ai_provider p ON p.id = m.provider_id
                 WHERE a.id = :id
                   AND m.deleted = FALSE
                   AND p.deleted = FALSE
                """, Map.of("id", id), this::mapAlias).stream().findFirst();
    }

    @Override
    public List<ModelAlias> findAliases(String alias) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id,
                       a.alias_code,
                       a.model_id,
                       m.display_name AS model_display_name,
                       p.name AS provider_name,
                       a.scene,
                       a.priority,
                       a.enabled,
                       a.create_time,
                       a.update_time,
                       a.create_user,
                       a.update_user
                  FROM ai_model_alias a
                  JOIN ai_model m ON m.id = a.model_id
                  JOIN ai_provider p ON p.id = m.provider_id
                 WHERE m.deleted = FALSE
                   AND p.deleted = FALSE
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (hasText(alias)) {
            sql.append(" AND a.alias_code = :alias");
            parameters.addValue("alias", alias);
        }
        sql.append(" ORDER BY a.alias_code, a.priority, LOWER(m.display_name)");
        return jdbc.query(sql.toString(), parameters, this::mapAlias);
    }

    @Override
    public List<ModelData> resolveAlias(String alias) {
        List<String> modelIds = jdbc.queryForList("""
                SELECT a.model_id
                  FROM ai_model_alias a
                  JOIN ai_model m ON m.id = a.model_id
                  JOIN ai_provider p ON p.id = m.provider_id
                 WHERE a.alias_code = :alias
                   AND a.enabled = TRUE
                   AND m.deleted = FALSE
                   AND m.status = 'ENABLED'
                   AND m.enabled = TRUE
                   AND m.available_from_provider = TRUE
                   AND p.deleted = FALSE
                   AND p.enabled = TRUE
                 ORDER BY a.priority ASC, m.recommended DESC, m.favorite DESC
                """, Map.of("alias", alias), String.class);
        return modelIds.stream().map(this::findById).flatMap(Optional::stream).toList();
    }

    @Override
    @Transactional
    public void synchronizeDiscovered(
            String providerId,
            List<DiscoveredModel> models,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_model
                   SET available_from_provider = FALSE,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :providerId
                   AND deleted = FALSE
                """, Map.of(
                "providerId", providerId,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
        for (DiscoveredModel discovered : models) {
            Optional<DiscoveryRecord> existing = findDiscoveryRecord(providerId, discovered.modelId());
            if (existing.isEmpty()) {
                insertDiscovered(providerId, discovered, now, actor);
            } else if (!existing.get().deleted()) {
                synchronizeExisting(existing.get().id(), discovered, now, actor);
            }
        }
    }

    @Override
    public void addAudit(AuditEntry entry) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
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
                .addValue("updateUser", entry.createUser());
        jdbc.update("""
                INSERT INTO ai_audit_log(
                    id, resource_type, resource_id, action, result, detail, trace_id,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :resourceType, :resourceId, :action, :result, :detail, :traceId,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, parameters);
    }

    @Override
    public List<AuditEntry> findAudit(String modelId) {
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
                 WHERE resource_type IN ('AI_MODEL', 'AI_MODEL_ALIAS')
                   AND resource_id = :modelId
                 ORDER BY create_time DESC
                """, Map.of("modelId", modelId), this::mapAudit);
    }

    private void insertDiscovered(
            String providerId,
            DiscoveredModel discovered,
            Instant now,
            String actor
    ) {
        String id = UUID.randomUUID().toString();
        Set<Capability> capabilities = discovered.capabilities();
        MapSqlParameterSource model = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("providerId", providerId)
                .addValue("remoteModelId", discovered.modelId())
                .addValue("displayName", friendlyName(discovered.displayName(), discovered.modelId()))
                .addValue("category", primaryCategory(capabilities).name())
                .addValue("contextLength", discovered.contextLength())
                .addValue("now", now.toString())
                .addValue("actor", actor);
        jdbc.update("""
                INSERT INTO ai_model(
                    id, provider_id, remote_model_id, display_name, category, description,
                    status, enabled, available_from_provider, recommended, favorite,
                    max_context_tokens, max_input_tokens, max_output_tokens, default_max_tokens,
                    context_manually_overridden, last_discovered_at, deleted,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :providerId, :remoteModelId, :displayName, :category, NULL,
                    'DISCOVERED', FALSE, TRUE, FALSE, FALSE,
                    :contextLength, NULL, NULL, NULL,
                    FALSE, :now, FALSE,
                    :now, :now, :actor, :actor
                )
                """, model);
        MapSqlParameterSource capabilityValues = capabilityParameters(
                id,
                capabilities,
                Map.of(),
                now,
                actor
        ).addValue("capabilityId", UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO ai_model_capability(
                    id, model_id, chat, reasoning, vision, embedding, rerank, image, video,
                    audio, speech, moderation, ocr, tool_call, json_mode, streaming,
                    manual_override_json, create_time, update_time, create_user, update_user
                ) VALUES (
                    :capabilityId, :id, :chat, :reasoning, :vision, :embedding, :rerank, :image, :video,
                    :audio, :speech, :moderation, :ocr, :toolCall, :jsonMode, :streaming,
                    :manualOverrides, :updateTime, :updateTime, :updateUser, :updateUser
                )
                """, capabilityValues);
        jdbc.update("""
                INSERT INTO ai_model_parameter(
                    id, model_id, temperature, top_p, frequency_penalty, presence_penalty,
                    max_output_tokens, reasoning_effort, seed,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :parameterId, :id, NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL,
                    :now, :now, :actor, :actor
                )
                """, model.addValue("parameterId", UUID.randomUUID().toString()));
    }

    private void synchronizeExisting(
            String id,
            DiscoveredModel discovered,
            Instant now,
            String actor
    ) {
        ModelData current = findById(id).orElseThrow();
        EnumSet<Capability> merged = discovered.capabilities().isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(discovered.capabilities());
        current.capabilityOverrides().forEach((capability, enabled) -> {
            if (enabled) {
                merged.add(capability);
            } else {
                merged.remove(capability);
            }
        });
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("contextLength", discovered.contextLength())
                .addValue("category", primaryCategory(merged).name())
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor)
                .addValue("lastDiscoveredAt", now.toString())
                .addValue("updateCategory", current.status() == ModelStatus.DISCOVERED);
        jdbc.update("""
                UPDATE ai_model
                   SET available_from_provider = TRUE,
                       max_context_tokens = CASE
                           WHEN context_manually_overridden = FALSE THEN :contextLength
                           ELSE max_context_tokens
                       END,
                       category = CASE
                           WHEN :updateCategory = TRUE THEN :category
                           ELSE category
                       END,
                       last_discovered_at = :lastDiscoveredAt,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, parameters);
        updateCapabilities(id, merged, current.capabilityOverrides(), now, actor);
    }

    private Optional<DiscoveryRecord> findDiscoveryRecord(String providerId, String remoteModelId) {
        return jdbc.query("""
                SELECT id, deleted
                  FROM ai_model
                 WHERE provider_id = :providerId
                   AND remote_model_id = :remoteModelId
                """, Map.of(
                "providerId", providerId,
                "remoteModelId", remoteModelId
        ), (resultSet, rowNumber) -> new DiscoveryRecord(
                resultSet.getString("id"),
                resultSet.getBoolean("deleted")
        )).stream().findFirst();
    }

    private ModelData attachRelations(ModelData model) {
        return attachRelations(
                model,
                findPricing(model.id()),
                findAliasesForModel(model.id()),
                findTags(model.id())
        );
    }

    private List<ModelData> attachRelations(List<ModelData> models) {
        if (models.isEmpty()) {
            return List.of();
        }
        List<String> modelIds = models.stream().map(ModelData::id).toList();
        Map<String, List<ModelPricing>> pricingByModel = new HashMap<>();
        jdbc.query("""
                SELECT id,
                       model_id,
                       currency,
                       prompt_price,
                       completion_price,
                       cache_read_price,
                       cache_write_price,
                       effective_time,
                       source,
                       notes,
                       create_time,
                       create_user
                  FROM ai_model_pricing
                 WHERE model_id IN (:modelIds)
                 ORDER BY model_id, effective_time DESC
                """, Map.of("modelIds", modelIds), this::mapPricing).forEach(pricing ->
                pricingByModel.computeIfAbsent(pricing.modelId(), ignored -> new ArrayList<>()).add(pricing)
        );

        Map<String, List<ModelAlias>> aliasesByModel = new HashMap<>();
        jdbc.query("""
                SELECT a.id,
                       a.alias_code,
                       a.model_id,
                       m.display_name AS model_display_name,
                       p.name AS provider_name,
                       a.scene,
                       a.priority,
                       a.enabled,
                       a.create_time,
                       a.update_time,
                       a.create_user,
                       a.update_user
                  FROM ai_model_alias a
                  JOIN ai_model m ON m.id = a.model_id
                  JOIN ai_provider p ON p.id = m.provider_id
                 WHERE a.model_id IN (:modelIds)
                   AND m.deleted = FALSE
                   AND p.deleted = FALSE
                 ORDER BY a.model_id, a.alias_code, a.priority
                """, Map.of("modelIds", modelIds), this::mapAlias).forEach(alias ->
                aliasesByModel.computeIfAbsent(alias.modelId(), ignored -> new ArrayList<>()).add(alias)
        );

        Map<String, Set<String>> tagsByModel = new HashMap<>();
        jdbc.query("""
                SELECT model_id, tag
                  FROM ai_model_tag
                 WHERE model_id IN (:modelIds)
                 ORDER BY model_id, tag
                """, Map.of("modelIds", modelIds), (resultSet, rowNumber) -> new ModelTag(
                resultSet.getString("model_id"),
                resultSet.getString("tag")
        )).forEach(tag ->
                tagsByModel.computeIfAbsent(tag.modelId(), ignored -> new LinkedHashSet<>()).add(tag.tag())
        );

        return models.stream()
                .map(model -> attachRelations(
                        model,
                        pricingByModel.getOrDefault(model.id(), List.of()),
                        aliasesByModel.getOrDefault(model.id(), List.of()),
                        tagsByModel.getOrDefault(model.id(), Set.of())
                ))
                .toList();
    }

    private ModelData attachRelations(
            ModelData model,
            List<ModelPricing> pricing,
            List<ModelAlias> aliases,
            Set<String> tags
    ) {
        return new ModelData(
                model.id(),
                model.providerId(),
                model.providerCode(),
                model.providerName(),
                model.providerEnabled(),
                model.providerLatencyMs(),
                model.remoteModelId(),
                model.displayName(),
                model.category(),
                model.description(),
                model.status(),
                model.enabled(),
                model.availableFromProvider(),
                model.recommended(),
                model.favorite(),
                model.maxContextTokens(),
                model.maxInputTokens(),
                model.maxOutputTokens(),
                model.defaultMaxTokens(),
                model.contextManuallyOverridden(),
                model.capabilities(),
                model.capabilityOverrides(),
                model.parameters(),
                pricing,
                aliases,
                tags,
                model.lastDiscoveredAt(),
                model.createTime(),
                model.updateTime(),
                model.createUser(),
                model.updateUser()
        );
    }

    private ModelData mapModel(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModelData(
                resultSet.getString("id"),
                resultSet.getString("provider_id"),
                resultSet.getString("provider_code"),
                resultSet.getString("provider_name"),
                resultSet.getBoolean("provider_enabled"),
                nullableLong(resultSet, "provider_latency_ms"),
                resultSet.getString("remote_model_id"),
                resultSet.getString("display_name"),
                ModelCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("description"),
                ModelStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("available_from_provider"),
                resultSet.getBoolean("recommended"),
                resultSet.getBoolean("favorite"),
                nullableInteger(resultSet, "max_context_tokens"),
                nullableInteger(resultSet, "max_input_tokens"),
                nullableInteger(resultSet, "max_output_tokens"),
                nullableInteger(resultSet, "default_max_tokens"),
                resultSet.getBoolean("context_manually_overridden"),
                mapCapabilities(resultSet),
                readCapabilityOverrides(resultSet.getString("manual_override_json")),
                new ModelParameters(
                        nullableDouble(resultSet, "temperature"),
                        nullableDouble(resultSet, "top_p"),
                        nullableDouble(resultSet, "frequency_penalty"),
                        nullableDouble(resultSet, "presence_penalty"),
                        nullableInteger(resultSet, "parameter_max_output_tokens"),
                        resultSet.getString("reasoning_effort"),
                        nullableLong(resultSet, "seed")
                ),
                List.of(),
                List.of(),
                Set.of(),
                parseInstant(resultSet.getString("last_discovered_at")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private ModelPricing mapPricing(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModelPricing(
                resultSet.getString("id"),
                resultSet.getString("model_id"),
                resultSet.getString("currency"),
                nullableBigDecimal(resultSet, "prompt_price"),
                nullableBigDecimal(resultSet, "completion_price"),
                nullableBigDecimal(resultSet, "cache_read_price"),
                nullableBigDecimal(resultSet, "cache_write_price"),
                Instant.parse(resultSet.getString("effective_time")),
                resultSet.getString("source"),
                resultSet.getString("notes"),
                Instant.parse(resultSet.getString("create_time")),
                resultSet.getString("create_user")
        );
    }

    private ModelAlias mapAlias(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModelAlias(
                resultSet.getString("id"),
                resultSet.getString("alias_code"),
                resultSet.getString("model_id"),
                resultSet.getString("model_display_name"),
                resultSet.getString("provider_name"),
                resultSet.getString("scene"),
                resultSet.getInt("priority"),
                resultSet.getBoolean("enabled"),
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

    private Set<String> findTags(String modelId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT tag
                  FROM ai_model_tag
                 WHERE model_id = :modelId
                 ORDER BY tag
                """, Map.of("modelId", modelId), String.class));
    }

    private List<ModelAlias> findAliasesForModel(String modelId) {
        return jdbc.query("""
                SELECT a.id,
                       a.alias_code,
                       a.model_id,
                       m.display_name AS model_display_name,
                       p.name AS provider_name,
                       a.scene,
                       a.priority,
                       a.enabled,
                       a.create_time,
                       a.update_time,
                       a.create_user,
                       a.update_user
                  FROM ai_model_alias a
                  JOIN ai_model m ON m.id = a.model_id
                  JOIN ai_provider p ON p.id = m.provider_id
                 WHERE a.model_id = :modelId
                 ORDER BY a.alias_code, a.priority
                """, Map.of("modelId", modelId), this::mapAlias);
    }

    private void replaceTags(String modelId, Set<String> tags, Instant now, String actor) {
        jdbc.update("DELETE FROM ai_model_tag WHERE model_id = :modelId", Map.of("modelId", modelId));
        for (String tag : tags) {
            jdbc.update("""
                    INSERT INTO ai_model_tag(
                        id, model_id, tag,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :modelId, :tag,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("modelId", modelId)
                    .addValue("tag", tag)
                    .addValue("createTime", now.toString())
                    .addValue("updateTime", now.toString())
                    .addValue("createUser", actor)
                    .addValue("updateUser", actor));
        }
    }

    private MapSqlParameterSource capabilityParameters(
            String id,
            Set<Capability> capabilities,
            Map<Capability, Boolean> manualOverrides,
            Instant now,
            String actor
    ) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("chat", capabilities.contains(Capability.CHAT))
                .addValue("reasoning", capabilities.contains(Capability.REASONING))
                .addValue("vision", capabilities.contains(Capability.VISION))
                .addValue("embedding", capabilities.contains(Capability.EMBEDDING))
                .addValue("rerank", capabilities.contains(Capability.RERANK))
                .addValue("image", capabilities.contains(Capability.IMAGE))
                .addValue("video", capabilities.contains(Capability.VIDEO))
                .addValue("audio", capabilities.contains(Capability.AUDIO))
                .addValue("speech", capabilities.contains(Capability.SPEECH))
                .addValue("moderation", capabilities.contains(Capability.MODERATION))
                .addValue("ocr", capabilities.contains(Capability.OCR))
                .addValue("toolCall", capabilities.contains(Capability.TOOL_CALL))
                .addValue("jsonMode", capabilities.contains(Capability.JSON_MODE))
                .addValue("streaming", capabilities.contains(Capability.STREAMING))
                .addValue("manualOverrides", writeJson(manualOverrides))
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
    }

    private Set<Capability> mapCapabilities(ResultSet resultSet) throws SQLException {
        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        addIf(resultSet.getBoolean("chat"), Capability.CHAT, capabilities);
        addIf(resultSet.getBoolean("reasoning"), Capability.REASONING, capabilities);
        addIf(resultSet.getBoolean("vision"), Capability.VISION, capabilities);
        addIf(resultSet.getBoolean("embedding"), Capability.EMBEDDING, capabilities);
        addIf(resultSet.getBoolean("rerank"), Capability.RERANK, capabilities);
        addIf(resultSet.getBoolean("image"), Capability.IMAGE, capabilities);
        addIf(resultSet.getBoolean("video"), Capability.VIDEO, capabilities);
        addIf(resultSet.getBoolean("audio"), Capability.AUDIO, capabilities);
        addIf(resultSet.getBoolean("speech"), Capability.SPEECH, capabilities);
        addIf(resultSet.getBoolean("moderation"), Capability.MODERATION, capabilities);
        addIf(resultSet.getBoolean("ocr"), Capability.OCR, capabilities);
        addIf(resultSet.getBoolean("tool_call"), Capability.TOOL_CALL, capabilities);
        addIf(resultSet.getBoolean("json_mode"), Capability.JSON_MODE, capabilities);
        addIf(resultSet.getBoolean("streaming"), Capability.STREAMING, capabilities);
        return Set.copyOf(capabilities);
    }

    private Map<Capability, Boolean> readCapabilityOverrides(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, CAPABILITY_OVERRIDES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read model capability overrides", exception);
        }
    }

    private String capabilityColumn(Capability capability) {
        return switch (capability) {
            case CHAT -> "chat";
            case REASONING -> "reasoning";
            case VISION -> "vision";
            case EMBEDDING -> "embedding";
            case RERANK -> "rerank";
            case IMAGE -> "image";
            case VIDEO -> "video";
            case AUDIO -> "audio";
            case SPEECH -> "speech";
            case MODERATION -> "moderation";
            case OCR -> "ocr";
            case TOOL_CALL -> "tool_call";
            case JSON_MODE -> "json_mode";
            case STREAMING -> "streaming";
        };
    }

    private ModelCategory primaryCategory(Set<Capability> capabilities) {
        List<Capability> order = List.of(
                Capability.REASONING,
                Capability.CHAT,
                Capability.VISION,
                Capability.EMBEDDING,
                Capability.RERANK,
                Capability.IMAGE,
                Capability.VIDEO,
                Capability.SPEECH,
                Capability.AUDIO,
                Capability.MODERATION,
                Capability.OCR
        );
        for (Capability capability : order) {
            if (capabilities.contains(capability)) {
                return switch (capability) {
                    case REASONING -> ModelCategory.REASONING;
                    case CHAT -> ModelCategory.CHAT;
                    case VISION -> ModelCategory.VISION;
                    case EMBEDDING -> ModelCategory.EMBEDDING;
                    case RERANK -> ModelCategory.RERANK;
                    case IMAGE -> ModelCategory.IMAGE;
                    case VIDEO -> ModelCategory.VIDEO;
                    case SPEECH -> ModelCategory.SPEECH;
                    case AUDIO -> ModelCategory.AUDIO;
                    case MODERATION -> ModelCategory.MODERATION;
                    case OCR -> ModelCategory.OCR;
                    default -> ModelCategory.OTHER;
                };
            }
        }
        return ModelCategory.OTHER;
    }

    private String friendlyName(String displayName, String remoteModelId) {
        if (hasText(displayName) && !displayName.equals(remoteModelId)) {
            return displayName.trim();
        }
        String[] words = remoteModelId.replace('_', '-').split("-");
        List<String> formatted = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (word.length() <= 3 || word.chars().allMatch(Character::isDigit)) {
                formatted.add(word.toUpperCase(Locale.ROOT));
            } else {
                formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return formatted.isEmpty() ? remoteModelId : String.join(" ", formatted);
    }

    private void addIf(boolean condition, Capability capability, Set<Capability> capabilities) {
        if (condition) {
            capabilities.add(capability);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize model data", exception);
        }
    }

    private Instant parseInstant(String value) {
        return hasText(value) ? Instant.parse(value) : null;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private BigDecimal nullableBigDecimal(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getBigDecimal(column);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DiscoveryRecord(String id, boolean deleted) {
    }

    private record ModelTag(String modelId, String tag) {
    }
}
