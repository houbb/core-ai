package io.coreplatform.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.ConversationModels.ChatResult;
import io.coreplatform.ai.application.domain.ConversationModels.ContextSnapshot;
import io.coreplatform.ai.application.domain.ConversationModels.Conversation;
import io.coreplatform.ai.application.domain.ConversationModels.ExportPackage;
import io.coreplatform.ai.application.domain.ConversationModels.Memory;
import io.coreplatform.ai.application.domain.ConversationModels.Message;
import io.coreplatform.ai.application.domain.ConversationModels.Session;
import io.coreplatform.ai.application.domain.ConversationModels.Share;
import io.coreplatform.ai.application.domain.ConversationModels.Summary;
import io.coreplatform.ai.application.domain.ConversationModels.View;
import io.coreplatform.ai.application.domain.GatewayModels.Invocation;
import io.coreplatform.ai.application.domain.GatewayModels.InvocationResult;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ConversationRepository;
import io.coreplatform.ai.application.port.ConversationSummaryPort;
import io.coreplatform.ai.application.port.GatewayInvocationPort;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository repository;
    private final ConversationSummaryPort summaryPort;
    private final GatewayInvocationPort gateway;
    private final RequestContextPort requestContext;
    private final ObjectMapper objectMapper;

    public ConversationService(
            ConversationRepository repository,
            ConversationSummaryPort summaryPort,
            GatewayInvocationPort gateway,
            RequestContextPort requestContext,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.summaryPort = summaryPort;
        this.gateway = gateway;
        this.requestContext = requestContext;
        this.objectMapper = objectMapper;
    }

    public List<Conversation> search(String query) {
        return repository.search(requestContext.actor(), query);
    }

    public View get(String id) {
        Conversation conversation = requireOwned(id);
        return view(conversation);
    }

    public View shared(String code) {
        Share share = repository.findShareByCode(code)
                .orElseThrow(() -> notFound("AI_CONVERSATION_SHARE_NOT_FOUND", "Conversation share not found"));
        if (share.revoked() || share.expiredAt() != null && share.expiredAt().isBefore(Instant.now())) {
            throw new ProviderOperationException(
                    "AI_CONVERSATION_SHARE_EXPIRED", "Conversation share is expired or revoked", 410
            );
        }
        Conversation conversation = repository.findConversation(share.conversationId())
                .orElseThrow(() -> notFound("AI_CONVERSATION_NOT_FOUND", "Conversation not found"));
        return view(conversation);
    }

    @Transactional
    public View create(String title, String sceneCode, List<String> tags) {
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Conversation conversation = new Conversation(
                UUID.randomUUID().toString(),
                required(title, "Conversation title", 300),
                actor,
                trim(sceneCode, 100),
                "ACTIVE",
                false,
                false,
                null,
                now,
                now,
                actor,
                actor
        );
        repository.insertConversation(conversation);
        repository.insertSession(new Session(
                UUID.randomUUID().toString(),
                conversation.id(),
                "Session 1",
                "ACTIVE",
                1,
                now,
                now,
                actor,
                actor
        ));
        repository.replaceTags(conversation.id(), normalizeTags(tags), actor);
        return view(conversation);
    }

    @Transactional
    public View update(
            String id,
            String title,
            String sceneCode,
            Boolean favorite,
            String status,
            List<String> tags
    ) {
        Conversation current = requireOwned(id);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String nextStatus = status == null || status.isBlank() ? current.status() : status.toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "ARCHIVED").contains(nextStatus)) {
            throw invalid("AI_CONVERSATION_STATUS_INVALID", "Conversation status must be ACTIVE or ARCHIVED");
        }
        Conversation updated = new Conversation(
                current.id(),
                title == null ? current.title() : required(title, "Conversation title", 300),
                current.ownerId(),
                sceneCode == null ? current.sceneCode() : trim(sceneCode, 100),
                nextStatus,
                favorite == null ? current.favorite() : favorite,
                current.deleted(),
                current.lastMessageTime(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        repository.updateConversation(updated);
        if (tags != null) {
            repository.replaceTags(id, normalizeTags(tags), actor);
        }
        return view(updated);
    }

    @Transactional
    public void delete(String id) {
        Conversation current = requireOwned(id);
        repository.updateConversation(new Conversation(
                current.id(), current.title(), current.ownerId(), current.sceneCode(),
                "ARCHIVED", current.favorite(), true, current.lastMessageTime(),
                current.createTime(), Instant.now(), current.createUser(), requestContext.actor()
        ));
    }

    @Transactional
    public Session newSession(String conversationId, String name) {
        Conversation conversation = requireOwned(conversationId);
        if (!"ACTIVE".equals(conversation.status())) {
            throw conflict("AI_CONVERSATION_ARCHIVED", "Archived Conversation cannot create a Session");
        }
        List<Session> sessions = repository.findSessions(conversationId);
        int sequence = sessions.stream().mapToInt(Session::sequenceNo).max().orElse(0) + 1;
        String actor = requestContext.actor();
        Instant now = Instant.now();
        return repository.insertSession(new Session(
                UUID.randomUUID().toString(),
                conversationId,
                name == null || name.isBlank() ? "Session " + sequence : trim(name, 200),
                "ACTIVE",
                sequence,
                now,
                now,
                actor,
                actor
        ));
    }

    @Transactional
    public ChatResult send(
            String conversationId,
            String sessionId,
            String content,
            String contentType,
            Map<String, Object> parameters
    ) {
        Conversation conversation = requireOwned(conversationId);
        if (!"ACTIVE".equals(conversation.status())) {
            throw conflict("AI_CONVERSATION_ARCHIVED", "Archived Conversation cannot receive messages");
        }
        Session session = selectSession(conversationId, sessionId);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        List<Message> history = repository.findCurrentMessages(conversationId);
        int sequence = history.stream()
                .filter(message -> message.sessionId().equals(session.id()))
                .mapToInt(Message::sequenceNo)
                .max().orElse(0) + 1;
        Message user = repository.insertMessage(new Message(
                UUID.randomUUID().toString(),
                session.id(),
                "USER",
                required(content, "Message content", 1_000_000),
                contentType == null || contentType.isBlank() ? "TEXT" : contentType.toUpperCase(Locale.ROOT),
                tokens(content),
                sequence,
                1,
                null,
                null,
                Map.of(),
                now,
                now,
                actor,
                actor
        ));
        List<Message> withUser = new ArrayList<>(history);
        withUser.add(user);
        ContextSnapshot context = buildContext(conversation, session, withUser);
        String gatewayInput = composeGatewayInput(conversation, withUser, context);
        InvocationResult result = gateway.invoke(new Invocation(
                UUID.randomUUID().toString(),
                requestContext.traceId(),
                conversation.sceneCode(),
                conversation.sceneCode() == null ? "conversation-default" : conversation.sceneCode(),
                gatewayInput,
                parameters,
                false,
                false,
                actor
        ));
        Message assistant = repository.insertMessage(new Message(
                UUID.randomUUID().toString(),
                session.id(),
                "ASSISTANT",
                result.output(),
                "TEXT",
                tokens(result.output()),
                sequence + 1,
                1,
                null,
                result.traceId(),
                Map.of(
                        "gatewayMode", result.mode(),
                        "executed", result.executed(),
                        "modelId", result.modelId() == null ? "" : result.modelId()
                ),
                Instant.now(),
                Instant.now(),
                "gateway",
                "gateway"
        ));
        Conversation updated = new Conversation(
                conversation.id(),
                conversation.title(),
                conversation.ownerId(),
                conversation.sceneCode(),
                conversation.status(),
                conversation.favorite(),
                conversation.deleted(),
                assistant.createTime(),
                conversation.createTime(),
                assistant.createTime(),
                conversation.createUser(),
                actor
        );
        repository.updateConversation(updated);
        maybeSummarize(updated, withAssistant(withUser, assistant));
        return new ChatResult(updated, user, assistant, context, result.mode(), result.executed());
    }

    @Transactional
    public Message editMessage(String messageId, String content) {
        Message current = repository.findMessage(messageId)
                .orElseThrow(() -> notFound("AI_MESSAGE_NOT_FOUND", "Message not found"));
        Conversation conversation = repository.findConversationBySession(current.sessionId())
                .orElseThrow(() -> notFound("AI_CONVERSATION_NOT_FOUND", "Conversation not found"));
        requireOwner(conversation);
        if (!"USER".equals(current.role())) {
            throw conflict("AI_MESSAGE_EDIT_FORBIDDEN", "Only User messages can be revised");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        return repository.insertMessage(new Message(
                UUID.randomUUID().toString(),
                current.sessionId(),
                current.role(),
                required(content, "Message content", 1_000_000),
                current.contentType(),
                tokens(content),
                current.sequenceNo(),
                current.version() + 1,
                current.id(),
                current.traceId(),
                Map.of("revision", true),
                now,
                now,
                actor,
                actor
        ));
    }

    @Transactional
    public Summary summarize(String conversationId) {
        Conversation conversation = requireOwned(conversationId);
        List<Message> messages = repository.findCurrentMessages(conversationId);
        return saveSummary(conversation, messages);
    }

    @Transactional
    public Share share(String conversationId, int expiresInHours) {
        requireOwned(conversationId);
        Instant now = Instant.now();
        return repository.insertShare(new Share(
                UUID.randomUUID().toString(),
                conversationId,
                UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                expiresInHours <= 0 ? null : now.plus(expiresInHours, ChronoUnit.HOURS),
                false,
                now,
                now
        ));
    }

    @Transactional
    public void revokeShare(String conversationId, String shareId) {
        requireOwned(conversationId);
        repository.revokeShare(shareId, requestContext.actor());
    }

    public ExportPackage export(String conversationId) {
        Conversation conversation = requireOwned(conversationId);
        return new ExportPackage(
                1,
                conversation,
                repository.findSessions(conversationId),
                repository.findCurrentMessages(conversationId),
                repository.findTags(conversationId),
                repository.findLatestSummary(conversationId).orElse(null)
        );
    }

    @Transactional
    public Map<String, Object> replay(String conversationId, String sourceMessageId) {
        Conversation conversation = requireOwned(conversationId);
        List<Message> messages = repository.findCurrentMessages(conversationId);
        Message source = sourceMessageId == null
                ? messages.stream().filter(message -> "USER".equals(message.role())).reduce((a, b) -> b)
                .orElseThrow(() -> conflict("AI_REPLAY_SOURCE_REQUIRED", "Conversation has no User message"))
                : repository.findMessage(sourceMessageId)
                .orElseThrow(() -> notFound("AI_MESSAGE_NOT_FOUND", "Message not found"));
        InvocationResult result = gateway.invoke(new Invocation(
                UUID.randomUUID().toString(),
                requestContext.traceId(),
                conversation.sceneCode(),
                conversation.sceneCode() == null ? "conversation-default" : conversation.sceneCode(),
                source.content(),
                Map.of("replay", true),
                false,
                false,
                requestContext.actor()
        ));
        repository.insertReplay(
                UUID.randomUUID().toString(),
                conversationId,
                source.id(),
                json(Map.of("messageId", source.id(), "contentHash", hash(source.content()))),
                json(Map.of("mode", result.mode(), "output", result.output())),
                result.status(),
                "PREVIEW",
                result.traceId(),
                requestContext.actor()
        );
        return Map.of(
                "mode", "PREVIEW",
                "dangerousToolsRequireNewApproval", true,
                "traceId", result.traceId(),
                "output", result.output()
        );
    }

    public List<Memory> memories(String ownerType, String ownerId) {
        String type = normalizeOwnerType(ownerType);
        requireMemoryAccess(type, ownerId);
        return repository.findMemories(type, ownerId);
    }

    @Transactional
    public Memory saveMemory(
            String id,
            String ownerType,
            String ownerId,
            String memoryType,
            String content,
            double importance,
            String source,
            Map<String, Object> metadata
    ) {
        String type = normalizeOwnerType(ownerType);
        requireMemoryAccess(type, ownerId);
        if (importance < 0 || importance > 1) {
            throw invalid("AI_MEMORY_IMPORTANCE_INVALID", "Memory importance must be between 0 and 1");
        }
        Memory current = id == null ? null : repository.findMemory(id)
                .orElseThrow(() -> notFound("AI_MEMORY_NOT_FOUND", "Memory not found"));
        if (current != null && current.frozen()) {
            throw conflict("AI_MEMORY_FROZEN", "Frozen Memory must be unfrozen before editing");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        return repository.saveMemory(new Memory(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                type,
                ownerId,
                required(memoryType, "Memory type", 32).toUpperCase(Locale.ROOT),
                required(content, "Memory content", 1_000_000),
                importance,
                source == null || source.isBlank() ? "MANUAL" : trim(source, 100),
                current != null && current.frozen(),
                false,
                metadata,
                current == null ? now : current.createTime(),
                now,
                current == null ? actor : current.createUser(),
                actor
        ));
    }

    @Transactional
    public Memory freezeMemory(String id, boolean frozen) {
        Memory current = repository.findMemory(id)
                .orElseThrow(() -> notFound("AI_MEMORY_NOT_FOUND", "Memory not found"));
        requireMemoryAccess(current.ownerType(), current.ownerId());
        return repository.saveMemory(new Memory(
                current.id(), current.ownerType(), current.ownerId(), current.memoryType(),
                current.content(), current.importance(), current.source(), frozen, current.deleted(),
                current.metadata(), current.createTime(), Instant.now(), current.createUser(),
                requestContext.actor()
        ));
    }

    @Transactional
    public void deleteMemory(String id) {
        Memory current = repository.findMemory(id)
                .orElseThrow(() -> notFound("AI_MEMORY_NOT_FOUND", "Memory not found"));
        requireMemoryAccess(current.ownerType(), current.ownerId());
        if (current.frozen()) {
            throw conflict("AI_MEMORY_FROZEN", "Frozen Memory cannot be deleted");
        }
        repository.saveMemory(new Memory(
                current.id(), current.ownerType(), current.ownerId(), current.memoryType(),
                current.content(), current.importance(), current.source(), current.frozen(), true,
                current.metadata(), current.createTime(), Instant.now(), current.createUser(),
                requestContext.actor()
        ));
    }

    private ContextSnapshot buildContext(
            Conversation conversation,
            Session session,
            List<Message> messages
    ) {
        Summary summary = repository.findLatestSummary(conversation.id()).orElse(null);
        List<Message> recent = messages.size() <= 12
                ? messages
                : messages.subList(messages.size() - 12, messages.size());
        String context = summaryPort.summarize(recent);
        List<Memory> memories = new ArrayList<>(repository.findMemories("USER", conversation.ownerId()));
        memories.addAll(repository.findMemories("CONVERSATION", conversation.id()));
        String memorySummary = memories.stream().limit(10)
                .map(memory -> memory.memoryType() + ": " + redact(memory.content()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("No long-term memory.");
        String storedContext = redact(context);
        Instant now = Instant.now();
        return repository.insertContext(new ContextSnapshot(
                UUID.randomUUID().toString(),
                session.id(),
                conversation.sceneCode() == null ? "conversation-default" : conversation.sceneCode(),
                storedContext,
                memorySummary,
                hash((summary == null ? "" : summary.text()) + storedContext + memorySummary),
                tokens(storedContext + memorySummary),
                false,
                now.plus(7, ChronoUnit.DAYS),
                now
        ));
    }

    private void maybeSummarize(Conversation conversation, List<Message> messages) {
        Summary latest = repository.findLatestSummary(conversation.id()).orElse(null);
        int end = messages.stream().mapToInt(Message::sequenceNo).max().orElse(0);
        int lastEnd = latest == null ? 0 : latest.messageEnd();
        if (messages.size() >= 8 && end - lastEnd >= 8) {
            saveSummary(conversation, messages);
        }
    }

    private Summary saveSummary(Conversation conversation, List<Message> messages) {
        if (messages.isEmpty()) {
            throw conflict("AI_SUMMARY_EMPTY", "Conversation has no messages to summarize");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        return repository.insertSummary(new Summary(
                UUID.randomUUID().toString(),
                conversation.id(),
                summaryPort.summarize(messages),
                messages.stream().mapToInt(Message::sequenceNo).min().orElse(1),
                messages.stream().mapToInt(Message::sequenceNo).max().orElse(1),
                summaryPort.mode(),
                now,
                now,
                actor,
                actor
        ));
    }

    private String composeGatewayInput(
            Conversation conversation,
            List<Message> messages,
            ContextSnapshot context
    ) {
        Message latest = messages.get(messages.size() - 1);
        return "Conversation: " + conversation.title()
                + "\nContext: " + context.contextSummary()
                + "\nMemory: " + context.memorySummary()
                + "\nUser: " + latest.content();
    }

    private List<Message> withAssistant(List<Message> messages, Message assistant) {
        List<Message> result = new ArrayList<>(messages);
        result.add(assistant);
        return result;
    }

    private Session selectSession(String conversationId, String sessionId) {
        List<Session> sessions = repository.findSessions(conversationId);
        return sessionId == null || sessionId.isBlank()
                ? sessions.stream().reduce((left, right) -> right)
                .orElseThrow(() -> notFound("AI_SESSION_NOT_FOUND", "Conversation Session not found"))
                : sessions.stream().filter(item -> item.id().equals(sessionId)).findFirst()
                .orElseThrow(() -> notFound("AI_SESSION_NOT_FOUND", "Conversation Session not found"));
    }

    private View view(Conversation conversation) {
        return new View(
                conversation,
                repository.findSessions(conversation.id()),
                repository.findCurrentMessages(conversation.id()),
                repository.findTags(conversation.id()),
                repository.findLatestSummary(conversation.id()).orElse(null)
        );
    }

    private Conversation requireOwned(String id) {
        Conversation conversation = repository.findConversation(id)
                .orElseThrow(() -> notFound("AI_CONVERSATION_NOT_FOUND", "Conversation not found"));
        requireOwner(conversation);
        if (conversation.deleted()) {
            throw new ProviderOperationException(
                    "AI_CONVERSATION_DELETED", "Conversation has been deleted", 410
            );
        }
        return conversation;
    }

    private void requireOwner(Conversation conversation) {
        if (!requestContext.actor().equals(conversation.ownerId())) {
            throw new ProviderOperationException(
                    "AI_CONVERSATION_FORBIDDEN", "Conversation is not accessible", 403
            );
        }
    }

    private void requireMemoryAccess(String ownerType, String ownerId) {
        if ("USER".equals(ownerType) && !requestContext.actor().equals(ownerId)) {
            throw new ProviderOperationException("AI_MEMORY_FORBIDDEN", "User Memory is not accessible", 403);
        }
        if ("CONVERSATION".equals(ownerType)) {
            requireOwned(ownerId);
        }
    }

    private String normalizeOwnerType(String value) {
        String type = required(value, "Memory owner type", 32).toUpperCase(Locale.ROOT);
        if (!List.of("USER", "CONVERSATION", "ORGANIZATION").contains(type)) {
            throw invalid("AI_MEMORY_OWNER_INVALID", "Unsupported Memory owner type");
        }
        return type;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return new LinkedHashSet<>(tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> trim(tag, 100))
                .limit(20)
                .toList()).stream().toList();
    }

    private String redact(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*\\S+", "$1=***")
                .replaceAll("\\b\\d{17}[0-9Xx]\\b", "***");
    }

    private int tokens(String value) {
        return Math.max(1, (value == null ? 0 : value.length() + 3) / 4);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Conversation data", exception);
        }
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_CONVERSATION_FIELD_REQUIRED", label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw invalid("AI_CONVERSATION_FIELD_TOO_LONG", label + " exceeds " + max + " characters");
        }
        return trimmed;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private ProviderOperationException invalid(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException notFound(String code, String message) {
        return new ProviderOperationException(code, message, 404);
    }
}
