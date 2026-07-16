package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.KnowledgeModels.Chunk;
import io.coreplatform.ai.application.domain.KnowledgeModels.Document;
import io.coreplatform.ai.application.domain.KnowledgeModels.Knowledge;
import io.coreplatform.ai.application.domain.KnowledgeModels.RetrieverPolicy;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchHit;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchResult;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import io.coreplatform.ai.application.domain.KnowledgeModels.Version;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.KnowledgeRepository;
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
public class JdbcKnowledgeRepository implements KnowledgeRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge WHERE code = :code",
                Map.of("code", code),
                Long.class
        ) > 0;
    }

    @Override
    public List<Knowledge> search(String query) {
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT id, code, name, description, category, status, current_version,
                       published_version, visibility, project_code, department_code,
                       owner_user, progress, create_time, update_time, create_user, update_user
                  FROM ai_knowledge
                 WHERE :query IS NULL OR LOWER(code) LIKE :query OR LOWER(name) LIKE :query
                    OR LOWER(COALESCE(description, '')) LIKE :query
                 ORDER BY update_time DESC, code
                """, new MapSqlParameterSource("query", normalized), this::mapKnowledge);
    }

    @Override
    public Optional<Knowledge> findKnowledge(String id) {
        return findKnowledgeBy("id", id);
    }

    @Override
    public Optional<Knowledge> findKnowledgeByCode(String code) {
        return findKnowledgeBy("code", code);
    }

    @Override
    public void insertKnowledge(Knowledge value) {
        jdbc.update("""
                INSERT INTO ai_knowledge(
                    id, code, name, description, category, status, current_version,
                    published_version, visibility, project_code, department_code,
                    owner_user, progress,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :code, :name, :description, :category, :status, :currentVersion,
                    :publishedVersion, :visibility, :projectCode, :departmentCode,
                    :ownerUser, :progress,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, knowledgeParameters(value));
    }

    @Override
    public void updateKnowledge(Knowledge value) {
        jdbc.update("""
                UPDATE ai_knowledge
                   SET name = :name, description = :description, category = :category,
                       status = :status, current_version = :currentVersion,
                       published_version = :publishedVersion, visibility = :visibility,
                       project_code = :projectCode, department_code = :departmentCode,
                       progress = :progress, update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, knowledgeParameters(value));
    }

    @Override
    public Source saveSource(Source value) {
        MapSqlParameterSource parameters = sourceParameters(value);
        int updated = jdbc.update("""
                UPDATE ai_knowledge_source
                   SET source_type = :sourceType, name = :name, config_json = :config,
                       sync_status = :syncStatus, last_sync_time = :lastSyncTime,
                       enabled = :enabled, update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_knowledge_source(
                        id, knowledge_id, source_type, name, config_json,
                        sync_status, last_sync_time, enabled,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :knowledgeId, :sourceType, :name, :config,
                        :syncStatus, :lastSyncTime, :enabled,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
        return value;
    }

    @Override
    public List<Source> findSources(String knowledgeId) {
        return jdbc.query("""
                SELECT id, knowledge_id, source_type, name, config_json,
                       sync_status, last_sync_time, enabled,
                       create_time, update_time, create_user, update_user
                  FROM ai_knowledge_source
                 WHERE knowledge_id = :knowledgeId
                 ORDER BY create_time, id
                """, Map.of("knowledgeId", knowledgeId), this::mapSource);
    }

    @Override
    public Document insertDocument(Document value) {
        jdbc.update("""
                INSERT INTO ai_document(
                    id, knowledge_id, source_id, title, path, size_bytes, language,
                    mime_type, status, content, content_hash, metadata_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :knowledgeId, :sourceId, :title, :path, :sizeBytes, :language,
                    :mimeType, :status, :content, :contentHash, :metadata,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, documentParameters(value));
        return value;
    }

    @Override
    public void updateDocument(Document value) {
        jdbc.update("""
                UPDATE ai_document
                   SET title = :title, path = :path, size_bytes = :sizeBytes,
                       language = :language, mime_type = :mimeType, status = :status,
                       content = :content, content_hash = :contentHash, metadata_json = :metadata,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, documentParameters(value));
    }

    @Override
    public List<Document> findDocuments(String knowledgeId) {
        return jdbc.query("""
                SELECT id, knowledge_id, source_id, title, path, size_bytes, language,
                       mime_type, status, content, content_hash, metadata_json,
                       create_time, update_time, create_user, update_user
                  FROM ai_document
                 WHERE knowledge_id = :knowledgeId
                 ORDER BY create_time, id
                """, Map.of("knowledgeId", knowledgeId), this::mapDocument);
    }

    @Override
    public void replaceChunks(String documentId, List<Chunk> chunks) {
        jdbc.update("""
                DELETE FROM ai_embedding
                 WHERE chunk_id IN (SELECT id FROM ai_chunk WHERE document_id = :documentId)
                """, Map.of("documentId", documentId));
        jdbc.update("DELETE FROM ai_chunk WHERE document_id = :documentId", Map.of("documentId", documentId));
        for (Chunk value : chunks) {
            jdbc.update("""
                    INSERT INTO ai_chunk(
                        id, knowledge_id, document_id, chunk_no, content, token_count,
                        heading, page_no, metadata_json, permission_json, content_hash,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :knowledgeId, :documentId, :chunkNo, :content, :tokenCount,
                        :heading, :pageNo, :metadata, :permissions, :contentHash,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, chunkParameters(value));
            jdbc.update("""
                    INSERT INTO ai_embedding(
                        id, knowledge_id, chunk_id, model_alias, vector_id,
                        embedding_version, status,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :knowledgeId, :chunkId, 'local-hash', :vectorId,
                        1, 'READY',
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("knowledgeId", value.knowledgeId())
                    .addValue("chunkId", value.id())
                    .addValue("vectorId", "hash:" + value.contentHash())
                    .addValue("now", value.createTime().toString())
                    .addValue("actor", value.createUser()));
        }
        if (!chunks.isEmpty()) {
            String knowledgeId = chunks.get(0).knowledgeId();
            int documentCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_document WHERE knowledge_id = :id AND status = 'READY'",
                    Map.of("id", knowledgeId), Integer.class
            );
            int chunkCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_chunk WHERE knowledge_id = :id",
                    Map.of("id", knowledgeId), Integer.class
            );
            String now = Instant.now().toString();
            int updated = jdbc.update("""
                    UPDATE ai_index
                       SET status = 'READY', document_count = :documents, chunk_count = :chunks,
                           update_time = :now, update_user = 'knowledge'
                     WHERE knowledge_id = :knowledgeId AND index_type = 'HYBRID'
                    """, new MapSqlParameterSource()
                    .addValue("documents", documentCount)
                    .addValue("chunks", chunkCount)
                    .addValue("now", now)
                    .addValue("knowledgeId", knowledgeId));
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO ai_index(
                            id, knowledge_id, index_type, status, config_json,
                            document_count, chunk_count,
                            create_time, update_time, create_user, update_user
                        ) VALUES (
                            :id, :knowledgeId, 'HYBRID', 'READY', '{}',
                            :documents, :chunks,
                            :now, :now, 'knowledge', 'knowledge'
                        )
                        """, new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("knowledgeId", knowledgeId)
                        .addValue("documents", documentCount)
                        .addValue("chunks", chunkCount)
                        .addValue("now", now));
            }
        }
    }

    @Override
    public List<Chunk> findChunks(String knowledgeId) {
        return jdbc.query("""
                SELECT id, knowledge_id, document_id, chunk_no, content, token_count,
                       heading, page_no, metadata_json, permission_json, content_hash,
                       create_time, update_time, create_user, update_user
                  FROM ai_chunk
                 WHERE knowledge_id = :knowledgeId
                 ORDER BY document_id, chunk_no
                """, Map.of("knowledgeId", knowledgeId), this::mapChunk);
    }

    @Override
    public RetrieverPolicy savePolicy(RetrieverPolicy value, Instant now, String actor) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("knowledgeId", value.knowledgeId())
                .addValue("topK", value.topK())
                .addValue("strategy", value.strategy())
                .addValue("threshold", value.scoreThreshold())
                .addValue("mmr", value.mmrLambda())
                .addValue("filter", json(value.metadataFilter()))
                .addValue("timeWeight", value.timeWeight())
                .addValue("chunkStrategy", value.chunkStrategy())
                .addValue("chunkSize", value.chunkSize())
                .addValue("overlap", value.chunkOverlap())
                .addValue("now", now.toString())
                .addValue("actor", actor);
        int updated = jdbc.update("""
                UPDATE ai_retriever_policy
                   SET top_k = :topK, strategy = :strategy, score_threshold = :threshold,
                       mmr_lambda = :mmr, metadata_filter_json = :filter,
                       time_weight = :timeWeight, chunk_strategy = :chunkStrategy,
                       chunk_size = :chunkSize, chunk_overlap = :overlap,
                       update_time = :now, update_user = :actor
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_retriever_policy(
                        id, knowledge_id, top_k, strategy, score_threshold, mmr_lambda,
                        metadata_filter_json, time_weight, chunk_strategy, chunk_size, chunk_overlap,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :knowledgeId, :topK, :strategy, :threshold, :mmr,
                        :filter, :timeWeight, :chunkStrategy, :chunkSize, :overlap,
                        :now, :now, :actor, :actor
                    )
                    """, parameters);
        }
        return value;
    }

    @Override
    public RetrieverPolicy findPolicy(String knowledgeId) {
        return jdbc.query("""
                SELECT id, knowledge_id, top_k, strategy, score_threshold, mmr_lambda,
                       metadata_filter_json, time_weight, chunk_strategy, chunk_size, chunk_overlap
                  FROM ai_retriever_policy
                 WHERE knowledge_id = :knowledgeId
                """, Map.of("knowledgeId", knowledgeId), this::mapPolicy).stream().findFirst()
                .orElseThrow(() -> new ProviderOperationException(
                        "AI_KNOWLEDGE_POLICY_NOT_FOUND", "Knowledge Retriever Policy not found", 404
                ));
    }

    @Override
    public void replacePermissions(String knowledgeId, List<String> permissions, String actor) {
        jdbc.update("DELETE FROM ai_knowledge_permission WHERE knowledge_id = :id", Map.of("id", knowledgeId));
        String now = Instant.now().toString();
        for (String permission : permissions) {
            String[] parts = permission.split(":", 2);
            String type = parts.length == 2 ? parts[0] : "USER";
            String value = parts.length == 2 ? parts[1] : permission;
            jdbc.update("""
                    INSERT INTO ai_knowledge_permission(
                        id, knowledge_id, permission_type, permission_value,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :knowledgeId, :type, :value,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("knowledgeId", knowledgeId)
                    .addValue("type", type)
                    .addValue("value", value)
                    .addValue("now", now)
                    .addValue("actor", actor));
        }
    }

    @Override
    public List<String> findPermissions(String knowledgeId) {
        return jdbc.query("""
                SELECT permission_type, permission_value
                  FROM ai_knowledge_permission
                 WHERE knowledge_id = :knowledgeId
                 ORDER BY permission_type, permission_value
                """, Map.of("knowledgeId", knowledgeId),
                (rs, rowNum) -> rs.getString("permission_type") + ":" + rs.getString("permission_value"));
    }

    @Override
    public Version insertVersion(Version value) {
        jdbc.update("""
                INSERT INTO ai_knowledge_version(
                    id, knowledge_id, version, snapshot_json, published_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :knowledgeId, :version, :snapshot, :publishedTime,
                    :createTime, :createTime, :createUser, :createUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("knowledgeId", value.knowledgeId())
                .addValue("version", value.version())
                .addValue("snapshot", json(value.snapshot()))
                .addValue("publishedTime", instant(value.publishedTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("createUser", value.createUser()));
        return value;
    }

    @Override
    public List<Version> findVersions(String knowledgeId) {
        return jdbc.query("""
                SELECT id, knowledge_id, version, snapshot_json, published_time,
                       create_time, create_user
                  FROM ai_knowledge_version
                 WHERE knowledge_id = :knowledgeId
                 ORDER BY version DESC
                """, Map.of("knowledgeId", knowledgeId), this::mapVersion);
    }

    @Override
    public Optional<Version> findVersion(String knowledgeId, int version) {
        return jdbc.query("""
                SELECT id, knowledge_id, version, snapshot_json, published_time,
                       create_time, create_user
                  FROM ai_knowledge_version
                 WHERE knowledge_id = :knowledgeId AND version = :version
                """, new MapSqlParameterSource()
                .addValue("knowledgeId", knowledgeId)
                .addValue("version", version), this::mapVersion).stream().findFirst();
    }

    @Override
    public void insertSearchLog(SearchResult result, String questionHash, String actor) {
        String logId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO ai_search_log(
                    id, knowledge_id, question_hash, question_text, strategy,
                    result_count, latency_ms, trace_id, status,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :knowledgeId, :questionHash, NULL, :strategy,
                    :resultCount, :latencyMs, :traceId, 'SUCCESS',
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", logId)
                .addValue("knowledgeId", result.knowledgeId())
                .addValue("questionHash", questionHash)
                .addValue("strategy", result.strategy())
                .addValue("resultCount", result.hits().size())
                .addValue("latencyMs", result.latencyMs())
                .addValue("traceId", result.traceId())
                .addValue("now", result.completedAt().toString())
                .addValue("actor", actor));
        for (SearchHit hit : result.hits()) {
            jdbc.update("""
                    INSERT INTO ai_reference(
                        id, search_log_id, answer_id, chunk_id, score, rank_no, citation_label,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :logId, NULL, :chunkId, :score, :rank, :citation,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("logId", logId)
                    .addValue("chunkId", hit.chunkId())
                    .addValue("score", hit.score())
                    .addValue("rank", hit.rank())
                    .addValue("citation", hit.citation())
                    .addValue("now", result.completedAt().toString())
                    .addValue("actor", actor));
        }
    }

    private Optional<Knowledge> findKnowledgeBy(String column, String value) {
        return jdbc.query("""
                SELECT id, code, name, description, category, status, current_version,
                       published_version, visibility, project_code, department_code,
                       owner_user, progress, create_time, update_time, create_user, update_user
                  FROM ai_knowledge
                 WHERE """ + " " + column + " = :value", Map.of("value", value), this::mapKnowledge)
                .stream().findFirst();
    }

    private Knowledge mapKnowledge(ResultSet rs, int rowNum) throws SQLException {
        return new Knowledge(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("category"), rs.getString("status"),
                rs.getInt("current_version"), nullableInteger(rs, "published_version"),
                rs.getString("visibility"), rs.getString("project_code"),
                rs.getString("department_code"), rs.getString("owner_user"),
                rs.getInt("progress"), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Source mapSource(ResultSet rs, int rowNum) throws SQLException {
        return new Source(
                rs.getString("id"), rs.getString("knowledge_id"), rs.getString("source_type"),
                rs.getString("name"), objectMap(rs.getString("config_json")),
                rs.getString("sync_status"), parseInstant(rs.getString("last_sync_time")),
                rs.getBoolean("enabled"), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Document mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new Document(
                rs.getString("id"), rs.getString("knowledge_id"), rs.getString("source_id"),
                rs.getString("title"), rs.getString("path"), rs.getLong("size_bytes"),
                rs.getString("language"), rs.getString("mime_type"), rs.getString("status"),
                rs.getString("content"), rs.getString("content_hash"),
                objectMap(rs.getString("metadata_json")), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private Chunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new Chunk(
                rs.getString("id"), rs.getString("knowledge_id"), rs.getString("document_id"),
                rs.getInt("chunk_no"), rs.getString("content"), rs.getInt("token_count"),
                rs.getString("heading"), nullableInteger(rs, "page_no"),
                objectMap(rs.getString("metadata_json")), stringList(rs.getString("permission_json")),
                rs.getString("content_hash"), Instant.parse(rs.getString("create_time")),
                Instant.parse(rs.getString("update_time")), rs.getString("create_user"),
                rs.getString("update_user")
        );
    }

    private RetrieverPolicy mapPolicy(ResultSet rs, int rowNum) throws SQLException {
        return new RetrieverPolicy(
                rs.getString("id"), rs.getString("knowledge_id"), rs.getInt("top_k"),
                rs.getString("strategy"), rs.getDouble("score_threshold"),
                rs.getDouble("mmr_lambda"), objectMap(rs.getString("metadata_filter_json")),
                rs.getDouble("time_weight"), rs.getString("chunk_strategy"),
                rs.getInt("chunk_size"), rs.getInt("chunk_overlap")
        );
    }

    private Version mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new Version(
                rs.getString("id"), rs.getString("knowledge_id"), rs.getInt("version"),
                objectMap(rs.getString("snapshot_json")),
                parseInstant(rs.getString("published_time")),
                Instant.parse(rs.getString("create_time")), rs.getString("create_user")
        );
    }

    private MapSqlParameterSource knowledgeParameters(Knowledge value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("code", value.code())
                .addValue("name", value.name())
                .addValue("description", value.description())
                .addValue("category", value.category())
                .addValue("status", value.status())
                .addValue("currentVersion", value.currentVersion())
                .addValue("publishedVersion", value.publishedVersion())
                .addValue("visibility", value.visibility())
                .addValue("projectCode", value.projectCode())
                .addValue("departmentCode", value.departmentCode())
                .addValue("ownerUser", value.ownerUser())
                .addValue("progress", value.progress())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource sourceParameters(Source value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("knowledgeId", value.knowledgeId())
                .addValue("sourceType", value.sourceType())
                .addValue("name", value.name())
                .addValue("config", json(value.config()))
                .addValue("syncStatus", value.syncStatus())
                .addValue("lastSyncTime", instant(value.lastSyncTime()))
                .addValue("enabled", value.enabled())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource documentParameters(Document value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("knowledgeId", value.knowledgeId())
                .addValue("sourceId", value.sourceId())
                .addValue("title", value.title())
                .addValue("path", value.path())
                .addValue("sizeBytes", value.sizeBytes())
                .addValue("language", value.language())
                .addValue("mimeType", value.mimeType())
                .addValue("status", value.status())
                .addValue("content", value.content())
                .addValue("contentHash", value.contentHash())
                .addValue("metadata", json(value.metadata()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource chunkParameters(Chunk value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("knowledgeId", value.knowledgeId())
                .addValue("documentId", value.documentId())
                .addValue("chunkNo", value.chunkNo())
                .addValue("content", value.content())
                .addValue("tokenCount", value.tokenCount())
                .addValue("heading", value.heading())
                .addValue("pageNo", value.pageNo())
                .addValue("metadata", json(value.metadata()))
                .addValue("permissions", json(value.permissions()))
                .addValue("contentHash", value.contentHash())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Knowledge data", exception);
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
            throw new IllegalStateException("Unable to read Knowledge data", exception);
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
            throw new IllegalStateException("Unable to read Knowledge permissions", exception);
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
