package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ConversationModels {

    private ConversationModels() {
    }

    public record Conversation(
            String id,
            String title,
            String ownerId,
            String sceneCode,
            String status,
            boolean favorite,
            boolean deleted,
            Instant lastMessageTime,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Session(
            String id,
            String conversationId,
            String name,
            String status,
            int sequenceNo,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Message(
            String id,
            String sessionId,
            String role,
            String content,
            String contentType,
            int tokenCount,
            int sequenceNo,
            int version,
            String supersedesMessageId,
            String traceId,
            Map<String, Object> metadata,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Message {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Memory(
            String id,
            String ownerType,
            String ownerId,
            String memoryType,
            String content,
            double importance,
            String source,
            boolean frozen,
            boolean deleted,
            Map<String, Object> metadata,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Memory {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Summary(
            String id,
            String conversationId,
            String text,
            int messageStart,
            int messageEnd,
            String mode,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record ContextSnapshot(
            String id,
            String sessionId,
            String promptSummary,
            String contextSummary,
            String memorySummary,
            String contentHash,
            int tokenCount,
            boolean contentStored,
            Instant expireTime,
            Instant createTime
    ) {
    }

    public record Share(
            String id,
            String conversationId,
            String shareCode,
            Instant expiredAt,
            boolean revoked,
            Instant createTime,
            Instant updateTime
    ) {
    }

    public record View(
            Conversation conversation,
            List<Session> sessions,
            List<Message> messages,
            List<String> tags,
            Summary summary
    ) {
    }

    public record ChatResult(
            Conversation conversation,
            Message userMessage,
            Message assistantMessage,
            ContextSnapshot context,
            String gatewayMode,
            boolean executed
    ) {
    }

    public record ExportPackage(
            int formatVersion,
            Conversation conversation,
            List<Session> sessions,
            List<Message> messages,
            List<String> tags,
            Summary summary
    ) {
    }
}
