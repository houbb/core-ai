package io.coreplatform.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.ConversationModels.ContextSnapshot;
import io.coreplatform.ai.application.domain.ConversationModels.Conversation;
import io.coreplatform.ai.application.domain.ConversationModels.Memory;
import io.coreplatform.ai.application.domain.ConversationModels.Message;
import io.coreplatform.ai.application.domain.ConversationModels.Session;
import io.coreplatform.ai.application.domain.ConversationModels.Share;
import io.coreplatform.ai.application.domain.ConversationModels.Summary;
import io.coreplatform.ai.application.port.ConversationRepository;
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
public class JdbcConversationRepository implements ConversationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcConversationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Conversation> search(String ownerId, String query) {
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT id, title, owner_id, scene_code, status, favorite, deleted,
                       last_message_time, create_time, update_time, create_user, update_user
                  FROM ai_conversation
                 WHERE owner_id = :ownerId AND deleted = FALSE
                   AND (:query IS NULL OR LOWER(title) LIKE :query)
                 ORDER BY favorite DESC, COALESCE(last_message_time, update_time) DESC
                """, new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("query", normalized), this::mapConversation);
    }

    @Override
    public Optional<Conversation> findConversation(String id) {
        return jdbc.query("""
                SELECT id, title, owner_id, scene_code, status, favorite, deleted,
                       last_message_time, create_time, update_time, create_user, update_user
                  FROM ai_conversation
                 WHERE id = :id
                """, Map.of("id", id), this::mapConversation).stream().findFirst();
    }

    @Override
    public void insertConversation(Conversation value) {
        jdbc.update("""
                INSERT INTO ai_conversation(
                    id, title, owner_id, scene_code, status, favorite, deleted,
                    last_message_time, create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :title, :ownerId, :sceneCode, :status, :favorite, :deleted,
                    :lastMessageTime, :createTime, :updateTime, :createUser, :updateUser
                )
                """, conversationParameters(value));
    }

    @Override
    public void updateConversation(Conversation value) {
        jdbc.update("""
                UPDATE ai_conversation
                   SET title = :title, scene_code = :sceneCode, status = :status,
                       favorite = :favorite, deleted = :deleted,
                       last_message_time = :lastMessageTime,
                       update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, conversationParameters(value));
    }

    @Override
    public Session insertSession(Session value) {
        jdbc.update("""
                INSERT INTO ai_session(
                    id, conversation_id, session_name, status, sequence_no,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :conversationId, :name, :status, :sequenceNo,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, sessionParameters(value));
        return value;
    }

    @Override
    public List<Session> findSessions(String conversationId) {
        return jdbc.query("""
                SELECT id, conversation_id, session_name, status, sequence_no,
                       create_time, update_time, create_user, update_user
                  FROM ai_session
                 WHERE conversation_id = :conversationId
                 ORDER BY sequence_no
                """, Map.of("conversationId", conversationId), this::mapSession);
    }

    @Override
    public Message insertMessage(Message value) {
        jdbc.update("""
                INSERT INTO ai_message(
                    id, session_id, role, content, content_type, token_count,
                    sequence_no, version, supersedes_message_id, trace_id, metadata_json,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :sessionId, :role, :content, :contentType, :tokenCount,
                    :sequenceNo, :version, :supersedesId, :traceId, :metadata,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, messageParameters(value));
        return value;
    }

    @Override
    public List<Message> findCurrentMessages(String conversationId) {
        return jdbc.query("""
                SELECT m.id, m.session_id, m.role, m.content, m.content_type, m.token_count,
                       m.sequence_no, m.version, m.supersedes_message_id, m.trace_id,
                       m.metadata_json, m.create_time, m.update_time, m.create_user, m.update_user
                  FROM ai_message m
                  JOIN ai_session s ON s.id = m.session_id
                  JOIN (
                      SELECT session_id, sequence_no, MAX(version) AS max_version
                        FROM ai_message
                       GROUP BY session_id, sequence_no
                  ) latest
                    ON latest.session_id = m.session_id
                   AND latest.sequence_no = m.sequence_no
                   AND latest.max_version = m.version
                 WHERE s.conversation_id = :conversationId
                 ORDER BY s.sequence_no, m.sequence_no
                """, Map.of("conversationId", conversationId), this::mapMessage);
    }

    @Override
    public Optional<Message> findMessage(String id) {
        return jdbc.query("""
                SELECT id, session_id, role, content, content_type, token_count,
                       sequence_no, version, supersedes_message_id, trace_id,
                       metadata_json, create_time, update_time, create_user, update_user
                  FROM ai_message
                 WHERE id = :id
                """, Map.of("id", id), this::mapMessage).stream().findFirst();
    }

    @Override
    public Optional<Conversation> findConversationBySession(String sessionId) {
        return jdbc.query("""
                SELECT c.id, c.title, c.owner_id, c.scene_code, c.status, c.favorite, c.deleted,
                       c.last_message_time, c.create_time, c.update_time, c.create_user, c.update_user
                  FROM ai_conversation c
                  JOIN ai_session s ON s.conversation_id = c.id
                 WHERE s.id = :sessionId
                """, Map.of("sessionId", sessionId), this::mapConversation).stream().findFirst();
    }

    @Override
    public Summary insertSummary(Summary value) {
        jdbc.update("""
                INSERT INTO ai_summary(
                    id, conversation_id, summary, message_start, message_end, summary_mode,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :conversationId, :summary, :messageStart, :messageEnd, :mode,
                    :createTime, :updateTime, :createUser, :updateUser
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("conversationId", value.conversationId())
                .addValue("summary", value.text())
                .addValue("messageStart", value.messageStart())
                .addValue("messageEnd", value.messageEnd())
                .addValue("mode", value.mode())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser()));
        return value;
    }

    @Override
    public Optional<Summary> findLatestSummary(String conversationId) {
        return jdbc.query("""
                SELECT id, conversation_id, summary, message_start, message_end, summary_mode,
                       create_time, update_time, create_user, update_user
                  FROM ai_summary
                 WHERE conversation_id = :conversationId
                 ORDER BY message_end DESC, create_time DESC
                 LIMIT 1
                """, Map.of("conversationId", conversationId), this::mapSummary).stream().findFirst();
    }

    @Override
    public ContextSnapshot insertContext(ContextSnapshot value) {
        jdbc.update("""
                INSERT INTO ai_context_snapshot(
                    id, session_id, prompt_summary, context_summary, memory_summary,
                    content_hash, token_count, content_stored, expire_time,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :sessionId, :promptSummary, :contextSummary, :memorySummary,
                    :contentHash, :tokenCount, :contentStored, :expireTime,
                    :createTime, :createTime, 'conversation', 'conversation'
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("sessionId", value.sessionId())
                .addValue("promptSummary", value.promptSummary())
                .addValue("contextSummary", value.contextSummary())
                .addValue("memorySummary", value.memorySummary())
                .addValue("contentHash", value.contentHash())
                .addValue("tokenCount", value.tokenCount())
                .addValue("contentStored", value.contentStored())
                .addValue("expireTime", instant(value.expireTime()))
                .addValue("createTime", value.createTime().toString()));
        return value;
    }

    @Override
    public Memory saveMemory(Memory value) {
        MapSqlParameterSource parameters = memoryParameters(value);
        int updated = jdbc.update("""
                UPDATE ai_memory
                   SET memory_type = :memoryType, content = :content, importance = :importance,
                       source = :source, frozen = :frozen, deleted = :deleted,
                       metadata_json = :metadata, update_time = :updateTime, update_user = :updateUser
                 WHERE id = :id
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO ai_memory(
                        id, owner_type, owner_id, memory_type, content, importance,
                        source, frozen, deleted, metadata_json,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :ownerType, :ownerId, :memoryType, :content, :importance,
                        :source, :frozen, :deleted, :metadata,
                        :createTime, :updateTime, :createUser, :updateUser
                    )
                    """, parameters);
        }
        return value;
    }

    @Override
    public Optional<Memory> findMemory(String id) {
        return jdbc.query(memorySelect() + " WHERE id = :id", Map.of("id", id), this::mapMemory)
                .stream().findFirst();
    }

    @Override
    public List<Memory> findMemories(String ownerType, String ownerId) {
        return jdbc.query(memorySelect() + """
                 WHERE owner_type = :ownerType AND owner_id = :ownerId AND deleted = FALSE
                 ORDER BY frozen DESC, importance DESC, update_time DESC
                """, new MapSqlParameterSource()
                .addValue("ownerType", ownerType)
                .addValue("ownerId", ownerId), this::mapMemory);
    }

    @Override
    public void replaceTags(String conversationId, List<String> tags, String actor) {
        jdbc.update("DELETE FROM ai_conversation_tag WHERE conversation_id = :id", Map.of("id", conversationId));
        Instant now = Instant.now();
        for (String tag : tags) {
            jdbc.update("""
                    INSERT INTO ai_conversation_tag(
                        id, conversation_id, tag,
                        create_time, update_time, create_user, update_user
                    ) VALUES (
                        :id, :conversationId, :tag,
                        :now, :now, :actor, :actor
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("conversationId", conversationId)
                    .addValue("tag", tag)
                    .addValue("now", now.toString())
                    .addValue("actor", actor));
        }
    }

    @Override
    public List<String> findTags(String conversationId) {
        return jdbc.query("""
                SELECT tag
                  FROM ai_conversation_tag
                 WHERE conversation_id = :conversationId
                 ORDER BY tag
                """, Map.of("conversationId", conversationId), (rs, rowNum) -> rs.getString("tag"));
    }

    @Override
    public Share insertShare(Share value) {
        jdbc.update("""
                INSERT INTO ai_conversation_share(
                    id, conversation_id, share_code, expired_at, revoked,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :conversationId, :shareCode, :expiredAt, :revoked,
                    :createTime, :updateTime, 'conversation', 'conversation'
                )
                """, new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("conversationId", value.conversationId())
                .addValue("shareCode", value.shareCode())
                .addValue("expiredAt", instant(value.expiredAt()))
                .addValue("revoked", value.revoked())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString()));
        return value;
    }

    @Override
    public Optional<Share> findShareByCode(String code) {
        return jdbc.query("""
                SELECT id, conversation_id, share_code, expired_at, revoked,
                       create_time, update_time
                  FROM ai_conversation_share
                 WHERE share_code = :code
                """, Map.of("code", code), this::mapShare).stream().findFirst();
    }

    @Override
    public void revokeShare(String id, String actor) {
        jdbc.update("""
                UPDATE ai_conversation_share
                   SET revoked = TRUE, update_time = :now, update_user = :actor
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("now", Instant.now().toString())
                .addValue("actor", actor));
    }

    @Override
    public void insertReplay(
            String id,
            String conversationId,
            String sourceMessageId,
            String requestJson,
            String responseJson,
            String status,
            String mode,
            String traceId,
            String actor
    ) {
        String now = Instant.now().toString();
        jdbc.update("""
                INSERT INTO ai_replay_log(
                    id, conversation_id, source_message_id, request_json, response_json,
                    status, mode, trace_id,
                    create_time, update_time, create_user, update_user
                ) VALUES (
                    :id, :conversationId, :sourceMessageId, :requestJson, :responseJson,
                    :status, :mode, :traceId,
                    :now, :now, :actor, :actor
                )
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("conversationId", conversationId)
                .addValue("sourceMessageId", sourceMessageId)
                .addValue("requestJson", requestJson)
                .addValue("responseJson", responseJson)
                .addValue("status", status)
                .addValue("mode", mode)
                .addValue("traceId", traceId)
                .addValue("now", now)
                .addValue("actor", actor));
    }

    private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getString("id"), rs.getString("title"), rs.getString("owner_id"),
                rs.getString("scene_code"), rs.getString("status"), rs.getBoolean("favorite"),
                rs.getBoolean("deleted"), parseInstant(rs.getString("last_message_time")),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"), rs.getString("update_user")
        );
    }

    private Session mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new Session(
                rs.getString("id"), rs.getString("conversation_id"), rs.getString("session_name"),
                rs.getString("status"), rs.getInt("sequence_no"),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"), rs.getString("update_user")
        );
    }

    private Message mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new Message(
                rs.getString("id"), rs.getString("session_id"), rs.getString("role"),
                rs.getString("content"), rs.getString("content_type"), rs.getInt("token_count"),
                rs.getInt("sequence_no"), rs.getInt("version"),
                rs.getString("supersedes_message_id"), rs.getString("trace_id"),
                objectMap(rs.getString("metadata_json")),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"), rs.getString("update_user")
        );
    }

    private Summary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new Summary(
                rs.getString("id"), rs.getString("conversation_id"), rs.getString("summary"),
                rs.getInt("message_start"), rs.getInt("message_end"), rs.getString("summary_mode"),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"), rs.getString("update_user")
        );
    }

    private Memory mapMemory(ResultSet rs, int rowNum) throws SQLException {
        return new Memory(
                rs.getString("id"), rs.getString("owner_type"), rs.getString("owner_id"),
                rs.getString("memory_type"), rs.getString("content"), rs.getDouble("importance"),
                rs.getString("source"), rs.getBoolean("frozen"), rs.getBoolean("deleted"),
                objectMap(rs.getString("metadata_json")),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time")),
                rs.getString("create_user"), rs.getString("update_user")
        );
    }

    private Share mapShare(ResultSet rs, int rowNum) throws SQLException {
        return new Share(
                rs.getString("id"), rs.getString("conversation_id"), rs.getString("share_code"),
                parseInstant(rs.getString("expired_at")), rs.getBoolean("revoked"),
                Instant.parse(rs.getString("create_time")), Instant.parse(rs.getString("update_time"))
        );
    }

    private MapSqlParameterSource conversationParameters(Conversation value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("title", value.title())
                .addValue("ownerId", value.ownerId())
                .addValue("sceneCode", value.sceneCode())
                .addValue("status", value.status())
                .addValue("favorite", value.favorite())
                .addValue("deleted", value.deleted())
                .addValue("lastMessageTime", instant(value.lastMessageTime()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource sessionParameters(Session value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("conversationId", value.conversationId())
                .addValue("name", value.name())
                .addValue("status", value.status())
                .addValue("sequenceNo", value.sequenceNo())
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource messageParameters(Message value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("sessionId", value.sessionId())
                .addValue("role", value.role())
                .addValue("content", value.content())
                .addValue("contentType", value.contentType())
                .addValue("tokenCount", value.tokenCount())
                .addValue("sequenceNo", value.sequenceNo())
                .addValue("version", value.version())
                .addValue("supersedesId", value.supersedesMessageId())
                .addValue("traceId", value.traceId())
                .addValue("metadata", json(value.metadata()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private MapSqlParameterSource memoryParameters(Memory value) {
        return new MapSqlParameterSource()
                .addValue("id", value.id())
                .addValue("ownerType", value.ownerType())
                .addValue("ownerId", value.ownerId())
                .addValue("memoryType", value.memoryType())
                .addValue("content", value.content())
                .addValue("importance", value.importance())
                .addValue("source", value.source())
                .addValue("frozen", value.frozen())
                .addValue("deleted", value.deleted())
                .addValue("metadata", json(value.metadata()))
                .addValue("createTime", value.createTime().toString())
                .addValue("updateTime", value.updateTime().toString())
                .addValue("createUser", value.createUser())
                .addValue("updateUser", value.updateUser());
    }

    private String memorySelect() {
        return """
                SELECT id, owner_type, owner_id, memory_type, content, importance,
                       source, frozen, deleted, metadata_json,
                       create_time, update_time, create_user, update_user
                  FROM ai_memory
                """;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize conversation data", exception);
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
            throw new IllegalStateException("Unable to read conversation metadata", exception);
        }
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }
}
