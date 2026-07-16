package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderHealth;
import io.coreplatform.ai.application.domain.ProviderModel;
import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.domain.ProviderStatus;
import io.coreplatform.ai.application.domain.ProviderType;
import io.coreplatform.ai.application.port.ProviderRepository;
import io.coreplatform.ai.application.port.SecretCipherPort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcProviderRepository implements ProviderRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Set<Capability>> CAPABILITY_SET = new TypeReference<>() {
    };

    private static final String SELECT_PROVIDER = """
            SELECT p.id,
                   p.code,
                   p.name,
                   p.description,
                   p.provider_type,
                   p.endpoint,
                   p.enabled,
                   p.status,
                   p.priority,
                   p.weight,
                   p.timeout_seconds,
                   p.retry_count,
                   p.create_time,
                   p.update_time,
                   p.create_user,
                   p.update_user,
                   s.api_key_cipher,
                   s.api_key_mask,
                   s.organization,
                   s.proxy,
                   s.tls_verify,
                   s.headers_json,
                   s.custom_parameters_json,
                   c.chat,
                   c.vision,
                   c.embedding,
                   c.image,
                   c.audio,
                   c.speech,
                   c.rerank,
                   c.reasoning,
                   h.latency_ms,
                   h.availability,
                   h.rpm,
                   h.tpm,
                   h.last_success,
                   h.last_error,
                   h.last_error_message,
                   h.last_status_code,
                   (SELECT COUNT(*)
                      FROM ai_provider_model_cache m
                     WHERE m.provider_id = p.id
                       AND m.status = 'ACTIVE') AS model_count
              FROM ai_provider p
              JOIN ai_provider_secret s ON s.provider_id = p.id
              JOIN ai_provider_capability c ON c.provider_id = p.id
              JOIN ai_provider_health h ON h.provider_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SecretCipherPort secretCipher;
    private final RowMapper<ProviderData> providerRowMapper = this::mapProvider;

    public JdbcProviderRepository(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            SecretCipherPort secretCipher
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.secretCipher = secretCipher;
    }

    @Override
    public boolean existsByCode(String code, String excludedId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM ai_provider
                 WHERE code = :code
                   AND deleted = FALSE
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource("code", code);
        if (excludedId != null) {
            sql.append(" AND id <> :excludedId");
            parameters.addValue("excludedId", excludedId);
        }
        Integer count = jdbc.queryForObject(sql.toString(), parameters, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public ProviderData insert(ProviderData provider) {
        MapSqlParameterSource providerParameters = providerParameters(provider);
        jdbc.update("""
                INSERT INTO ai_provider(
                    id, code, name, description, provider_type, endpoint, enabled, status,
                    priority, weight, timeout_seconds, retry_count, deleted,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :providerType, :endpoint, :enabled, :status,
                    :priority, :weight, :timeoutSeconds, :retryCount, FALSE,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, providerParameters);

        jdbc.update("""
                INSERT INTO ai_provider_secret(
                    id, provider_id, api_key_cipher, api_key_mask, organization, proxy,
                    tls_verify, headers_json, custom_parameters_json, encrypted,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :secretId, :id, :apiKeyCipher, :apiKeyMask, :organization, :proxy,
                    :tlsVerify, :headersJson, :customParametersJson, TRUE,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, providerParameters
                .addValue("secretId", UUID.randomUUID().toString())
                .addValue("headersJson", encryptMap(provider.headers()))
                .addValue("customParametersJson", encryptMap(provider.customParameters())));

        jdbc.update("""
                INSERT INTO ai_provider_capability(
                    id, provider_id, chat, vision, embedding, image, audio, speech, rerank, reasoning,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :capabilityId, :id, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, providerParameters.addValue("capabilityId", UUID.randomUUID().toString()));

        jdbc.update("""
                INSERT INTO ai_provider_health(
                    id, provider_id, latency_ms, availability, rpm, tpm,
                    last_success, last_error, last_error_message, last_status_code,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :healthId, :id, NULL, 0, 0, 0,
                    NULL, NULL, NULL, NULL,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, providerParameters.addValue("healthId", UUID.randomUUID().toString()));
        replaceTags(provider.id(), provider.tags(), provider.createTime(), provider.createUser());
        return findById(provider.id()).orElseThrow();
    }

    @Override
    public ProviderData update(ProviderData provider) {
        MapSqlParameterSource parameters = providerParameters(provider);
        jdbc.update("""
                UPDATE ai_provider
                   SET code = :code,
                       name = :name,
                       description = :description,
                       provider_type = :providerType,
                       endpoint = :endpoint,
                       enabled = :enabled,
                       status = :status,
                       priority = :priority,
                       weight = :weight,
                       timeout_seconds = :timeoutSeconds,
                       retry_count = :retryCount,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                   AND deleted = FALSE
                """, parameters);
        jdbc.update("""
                UPDATE ai_provider_secret
                   SET api_key_cipher = :apiKeyCipher,
                       api_key_mask = :apiKeyMask,
                       organization = :organization,
                       proxy = :proxy,
                       tls_verify = :tlsVerify,
                       headers_json = :headersJson,
                       custom_parameters_json = :customParametersJson,
                       encrypted = TRUE,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :id
                """, parameters
                .addValue("headersJson", encryptMap(provider.headers()))
                .addValue("customParametersJson", encryptMap(provider.customParameters())));
        replaceTags(provider.id(), provider.tags(), provider.updateTime(), provider.updateUser());
        return findById(provider.id()).orElseThrow();
    }

    @Override
    public Optional<ProviderData> findById(String id) {
        List<ProviderData> providers = jdbc.query(
                SELECT_PROVIDER + " WHERE p.id = :id AND p.deleted = FALSE",
                Map.of("id", id),
                providerRowMapper
        );
        return providers.stream().findFirst().map(this::attachTags);
    }

    @Override
    public List<ProviderData> search(ProviderSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(SELECT_PROVIDER);
        sql.append(" WHERE p.deleted = FALSE");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (criteria != null) {
            if (hasText(criteria.query())) {
                sql.append("""
                         AND (
                            LOWER(p.code) LIKE :query
                            OR LOWER(p.name) LIKE :query
                            OR LOWER(COALESCE(p.description, '')) LIKE :query
                            OR LOWER(p.endpoint) LIKE :query
                            OR EXISTS (
                                SELECT 1
                                  FROM ai_provider_tag search_tag
                                 WHERE search_tag.provider_id = p.id
                                   AND LOWER(search_tag.tag) LIKE :query
                            )
                         )
                        """);
                parameters.addValue("query", "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%");
            }
            if (criteria.enabled() != null) {
                sql.append(" AND p.enabled = :enabled");
                parameters.addValue("enabled", criteria.enabled());
            }
            if (hasText(criteria.location())) {
                if ("LOCAL".equalsIgnoreCase(criteria.location())) {
                    sql.append(" AND p.provider_type IN ('OLLAMA', 'LM_STUDIO')");
                } else if ("CLOUD".equalsIgnoreCase(criteria.location())) {
                    sql.append(" AND p.provider_type NOT IN ('OLLAMA', 'LM_STUDIO')");
                }
            }
            if (criteria.capability() != null) {
                String capabilityColumn = capabilityColumn(criteria.capability());
                if (capabilityColumn == null) {
                    sql.append(" AND 1 = 0");
                } else {
                    sql.append(" AND c.").append(capabilityColumn).append(" = TRUE");
                }
            }
            if (hasText(criteria.tag())) {
                sql.append("""
                         AND EXISTS (
                            SELECT 1
                              FROM ai_provider_tag t
                             WHERE t.provider_id = p.id
                               AND LOWER(t.tag) = :tag
                         )
                        """);
                parameters.addValue("tag", criteria.tag().trim().toLowerCase(Locale.ROOT));
            }
        }
        sql.append(" ORDER BY p.priority ASC, p.weight DESC, LOWER(p.name) ASC");
        return jdbc.query(sql.toString(), parameters, providerRowMapper)
                .stream()
                .map(this::attachTags)
                .toList();
    }

    @Override
    public void updateStatus(
            String providerId,
            ProviderStatus status,
            boolean enabled,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_provider
                   SET status = :status,
                       enabled = :enabled,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                   AND deleted = FALSE
                """, Map.of(
                "status", status.name(),
                "enabled", enabled,
                "updateTime", now.toString(),
                "updateUser", actor,
                "id", providerId
        ));
    }

    @Override
    public void updateCapability(
            String providerId,
            Set<Capability> capabilities,
            Instant now,
            String actor
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", providerId)
                .addValue("chat", capabilities.contains(Capability.CHAT))
                .addValue("vision", capabilities.contains(Capability.VISION))
                .addValue("embedding", capabilities.contains(Capability.EMBEDDING))
                .addValue("image", capabilities.contains(Capability.IMAGE))
                .addValue("audio", capabilities.contains(Capability.AUDIO))
                .addValue("speech", capabilities.contains(Capability.SPEECH))
                .addValue("rerank", capabilities.contains(Capability.RERANK))
                .addValue("reasoning", capabilities.contains(Capability.REASONING))
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
        jdbc.update("""
                UPDATE ai_provider_capability
                   SET chat = :chat,
                       vision = :vision,
                       embedding = :embedding,
                       image = :image,
                       audio = :audio,
                       speech = :speech,
                       rerank = :rerank,
                       reasoning = :reasoning,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :id
                """, parameters);
    }

    @Override
    public void recordHealthSuccess(String providerId, long latencyMs, Instant now, String actor) {
        ProviderHealth current = findById(providerId).map(ProviderData::health).orElse(ProviderHealth.empty());
        double availability = current.lastSuccess() == null && current.lastError() == null
                ? 100
                : Math.min(100, current.availability() * 0.9 + 10);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", providerId)
                .addValue("latencyMs", latencyMs)
                .addValue("availability", availability)
                .addValue("lastSuccess", now.toString())
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
        jdbc.update("""
                UPDATE ai_provider_health
                   SET latency_ms = :latencyMs,
                       availability = :availability,
                       last_success = :lastSuccess,
                       last_error_message = NULL,
                       last_status_code = 200,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :id
                """, parameters);
    }

    @Override
    public void recordHealthFailure(
            String providerId,
            long latencyMs,
            Integer statusCode,
            String message,
            Instant now,
            String actor
    ) {
        ProviderHealth current = findById(providerId).map(ProviderData::health).orElse(ProviderHealth.empty());
        double availability = current.lastSuccess() == null && current.lastError() == null
                ? 0
                : Math.max(0, current.availability() * 0.9);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", providerId)
                .addValue("latencyMs", latencyMs)
                .addValue("availability", availability)
                .addValue("lastError", now.toString())
                .addValue("lastErrorMessage", message)
                .addValue("lastStatusCode", statusCode)
                .addValue("updateTime", now.toString())
                .addValue("updateUser", actor);
        jdbc.update("""
                UPDATE ai_provider_health
                   SET latency_ms = :latencyMs,
                       availability = :availability,
                       last_error = :lastError,
                       last_error_message = :lastErrorMessage,
                       last_status_code = :lastStatusCode,
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :id
                """, parameters);
    }

    @Override
    public void syncModels(
            String providerId,
            List<DiscoveredModel> models,
            Instant now,
            String actor
    ) {
        jdbc.update("""
                UPDATE ai_provider_model_cache
                   SET status = 'INACTIVE',
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE provider_id = :providerId
                """, Map.of(
                "providerId", providerId,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
        for (DiscoveredModel model : models) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                      FROM ai_provider_model_cache
                     WHERE provider_id = :providerId
                       AND model_id = :modelId
                    """, Map.of("providerId", providerId, "modelId", model.modelId()), Integer.class);
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("providerId", providerId)
                    .addValue("modelId", model.modelId())
                    .addValue("displayName", model.displayName())
                    .addValue("capabilities", writeJson(model.capabilities()))
                    .addValue("contextLength", model.contextLength())
                    .addValue("lastSyncAt", now.toString())
                    .addValue("createTime", now.toString())
                    .addValue("updateTime", now.toString())
                    .addValue("createUser", actor)
                    .addValue("updateUser", actor);
            if (count != null && count > 0) {
                jdbc.update("""
                        UPDATE ai_provider_model_cache
                           SET display_name = :displayName,
                               capability_json = :capabilities,
                               context_length = :contextLength,
                               status = 'ACTIVE',
                               last_sync_at = :lastSyncAt,
                               update_time = :updateTime,
                               update_user = :updateUser
                         WHERE provider_id = :providerId
                           AND model_id = :modelId
                        """, parameters);
            } else {
                jdbc.update("""
                        INSERT INTO ai_provider_model_cache(
                            id, provider_id, model_id, display_name, capability_json,
                            context_length, status, last_sync_at,
                            create_time, update_time, create_user, update_user
                        ) VALUES (
                            :id, :providerId, :modelId, :displayName, :capabilities,
                            :contextLength, 'ACTIVE', :lastSyncAt,
                            :createTime, :updateTime, :createUser, :updateUser
                        )
                        """, parameters);
            }
        }
    }

    @Override
    public List<ProviderModel> findModels(String providerId) {
        return jdbc.query("""
                SELECT id,
                       provider_id,
                       model_id,
                       display_name,
                       capability_json,
                       context_length,
                       status,
                       last_sync_at,
                       create_time,
                       update_time
                  FROM ai_provider_model_cache
                 WHERE provider_id = :providerId
                 ORDER BY status ASC, LOWER(display_name) ASC
                """, Map.of("providerId", providerId), this::mapModel);
    }

    @Override
    public void softDelete(String providerId, Instant now, String actor) {
        jdbc.update("""
                UPDATE ai_provider
                   SET deleted = TRUE,
                       enabled = FALSE,
                       status = 'DELETED',
                       update_time = :updateTime,
                       update_user = :updateUser
                 WHERE id = :id
                """, Map.of(
                "id", providerId,
                "updateTime", now.toString(),
                "updateUser", actor
        ));
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
    public List<AuditEntry> findAudit(String providerId) {
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
                 WHERE resource_type = 'AI_PROVIDER'
                   AND resource_id = :providerId
                 ORDER BY create_time DESC
                """, Map.of("providerId", providerId), this::mapAudit);
    }

    private ProviderData mapProvider(ResultSet resultSet, int rowNumber) throws SQLException {
        String providerId = resultSet.getString("id");
        return new ProviderData(
                providerId,
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                ProviderType.valueOf(resultSet.getString("provider_type")),
                resultSet.getString("endpoint"),
                resultSet.getBoolean("enabled"),
                ProviderStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("priority"),
                resultSet.getInt("weight"),
                resultSet.getInt("timeout_seconds"),
                resultSet.getInt("retry_count"),
                Set.of(),
                resultSet.getString("api_key_cipher"),
                resultSet.getString("api_key_mask"),
                resultSet.getString("organization"),
                resultSet.getString("proxy"),
                resultSet.getBoolean("tls_verify"),
                decryptMap(resultSet.getString("headers_json")),
                decryptMap(resultSet.getString("custom_parameters_json")),
                mapCapabilities(resultSet),
                new ProviderHealth(
                        nullableLong(resultSet, "latency_ms"),
                        resultSet.getDouble("availability"),
                        resultSet.getInt("rpm"),
                        resultSet.getInt("tpm"),
                        parseInstant(resultSet.getString("last_success")),
                        parseInstant(resultSet.getString("last_error")),
                        resultSet.getString("last_error_message"),
                        nullableInteger(resultSet, "last_status_code")
                ),
                resultSet.getInt("model_count"),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time")),
                resultSet.getString("create_user"),
                resultSet.getString("update_user")
        );
    }

    private ProviderModel mapModel(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProviderModel(
                resultSet.getString("id"),
                resultSet.getString("provider_id"),
                resultSet.getString("model_id"),
                resultSet.getString("display_name"),
                readCapabilities(resultSet.getString("capability_json")),
                nullableInteger(resultSet, "context_length"),
                resultSet.getString("status"),
                Instant.parse(resultSet.getString("last_sync_at")),
                Instant.parse(resultSet.getString("create_time")),
                Instant.parse(resultSet.getString("update_time"))
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

    private MapSqlParameterSource providerParameters(ProviderData provider) {
        return new MapSqlParameterSource()
                .addValue("id", provider.id())
                .addValue("code", provider.code())
                .addValue("name", provider.name())
                .addValue("description", provider.description())
                .addValue("providerType", provider.type().name())
                .addValue("endpoint", provider.endpoint())
                .addValue("enabled", provider.enabled())
                .addValue("status", provider.status().name())
                .addValue("priority", provider.priority())
                .addValue("weight", provider.weight())
                .addValue("timeoutSeconds", provider.timeoutSeconds())
                .addValue("retryCount", provider.retryCount())
                .addValue("apiKeyCipher", provider.encryptedApiKey())
                .addValue("apiKeyMask", provider.apiKeyMask())
                .addValue("organization", provider.organization())
                .addValue("proxy", provider.proxy())
                .addValue("tlsVerify", provider.tlsVerify())
                .addValue("createTime", provider.createTime().toString())
                .addValue("updateTime", provider.updateTime().toString())
                .addValue("createUser", provider.createUser())
                .addValue("updateUser", provider.updateUser());
    }

    private Set<String> findTags(String providerId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT tag
                  FROM ai_provider_tag
                 WHERE provider_id = :providerId
                 ORDER BY tag
                """, Map.of("providerId", providerId), String.class));
    }

    private ProviderData attachTags(ProviderData provider) {
        return new ProviderData(
                provider.id(),
                provider.code(),
                provider.name(),
                provider.description(),
                provider.type(),
                provider.endpoint(),
                provider.enabled(),
                provider.status(),
                provider.priority(),
                provider.weight(),
                provider.timeoutSeconds(),
                provider.retryCount(),
                findTags(provider.id()),
                provider.encryptedApiKey(),
                provider.apiKeyMask(),
                provider.organization(),
                provider.proxy(),
                provider.tlsVerify(),
                provider.headers(),
                provider.customParameters(),
                provider.capabilities(),
                provider.health(),
                provider.modelCount(),
                provider.createTime(),
                provider.updateTime(),
                provider.createUser(),
                provider.updateUser()
        );
    }

    private void replaceTags(String providerId, Set<String> tags, Instant now, String actor) {
        jdbc.update(
                "DELETE FROM ai_provider_tag WHERE provider_id = :providerId",
                Map.of("providerId", providerId)
        );
        for (String tag : tags) {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("providerId", providerId)
                    .addValue("tag", tag)
                    .addValue("createTime", now.toString())
                    .addValue("updateTime", now.toString())
                    .addValue("createUser", actor)
                    .addValue("updateUser", actor);
            jdbc.update("""
                    INSERT INTO ai_provider_tag(
                        id, provider_id, tag,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :providerId, :tag,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
    }

    private Set<Capability> mapCapabilities(ResultSet resultSet) throws SQLException {
        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        addIf(resultSet.getBoolean("chat"), Capability.CHAT, capabilities);
        addIf(resultSet.getBoolean("vision"), Capability.VISION, capabilities);
        addIf(resultSet.getBoolean("embedding"), Capability.EMBEDDING, capabilities);
        addIf(resultSet.getBoolean("image"), Capability.IMAGE, capabilities);
        addIf(resultSet.getBoolean("audio"), Capability.AUDIO, capabilities);
        addIf(resultSet.getBoolean("speech"), Capability.SPEECH, capabilities);
        addIf(resultSet.getBoolean("rerank"), Capability.RERANK, capabilities);
        addIf(resultSet.getBoolean("reasoning"), Capability.REASONING, capabilities);
        return Set.copyOf(capabilities);
    }

    private void addIf(boolean condition, Capability capability, Set<Capability> capabilities) {
        if (condition) {
            capabilities.add(capability);
        }
    }

    private String capabilityColumn(Capability capability) {
        return switch (capability) {
            case CHAT -> "chat";
            case VISION -> "vision";
            case EMBEDDING -> "embedding";
            case IMAGE -> "image";
            case AUDIO -> "audio";
            case SPEECH -> "speech";
            case RERANK -> "rerank";
            case REASONING -> "reasoning";
            case VIDEO, MODERATION, OCR, TOOL_CALL, JSON_MODE, STREAMING -> null;
        };
    }

    private String encryptMap(Map<String, String> value) {
        return secretCipher.encrypt(writeJson(value));
    }

    private Map<String, String> decryptMap(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(secretCipher.decrypt(value), STRING_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read encrypted provider configuration", exception);
        }
    }

    private Set<Capability> readCapabilities(String value) {
        if (!hasText(value)) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(value, CAPABILITY_SET);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read provider model capabilities", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize provider data", exception);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
