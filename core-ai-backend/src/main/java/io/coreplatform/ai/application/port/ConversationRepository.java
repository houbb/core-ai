package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ConversationModels.ContextSnapshot;
import io.coreplatform.ai.application.domain.ConversationModels.Conversation;
import io.coreplatform.ai.application.domain.ConversationModels.Memory;
import io.coreplatform.ai.application.domain.ConversationModels.Message;
import io.coreplatform.ai.application.domain.ConversationModels.Session;
import io.coreplatform.ai.application.domain.ConversationModels.Share;
import io.coreplatform.ai.application.domain.ConversationModels.Summary;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    List<Conversation> search(String ownerId, String query);

    Optional<Conversation> findConversation(String id);

    void insertConversation(Conversation conversation);

    void updateConversation(Conversation conversation);

    Session insertSession(Session session);

    List<Session> findSessions(String conversationId);

    Message insertMessage(Message message);

    List<Message> findCurrentMessages(String conversationId);

    Optional<Message> findMessage(String id);

    Optional<Conversation> findConversationBySession(String sessionId);

    Summary insertSummary(Summary summary);

    Optional<Summary> findLatestSummary(String conversationId);

    ContextSnapshot insertContext(ContextSnapshot snapshot);

    Memory saveMemory(Memory memory);

    Optional<Memory> findMemory(String id);

    List<Memory> findMemories(String ownerType, String ownerId);

    void replaceTags(String conversationId, List<String> tags, String actor);

    List<String> findTags(String conversationId);

    Share insertShare(Share share);

    Optional<Share> findShareByCode(String code);

    void revokeShare(String id, String actor);

    void insertReplay(
            String id,
            String conversationId,
            String sourceMessageId,
            String requestJson,
            String responseJson,
            String status,
            String mode,
            String traceId,
            String actor
    );
}
