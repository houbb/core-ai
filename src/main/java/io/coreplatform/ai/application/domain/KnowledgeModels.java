package io.coreplatform.ai.application.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class KnowledgeModels {

    private KnowledgeModels() {
    }

    public record Knowledge(
            String id,
            String code,
            String name,
            String description,
            String category,
            String status,
            int currentVersion,
            Integer publishedVersion,
            String visibility,
            String projectCode,
            String departmentCode,
            String ownerUser,
            int progress,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record Source(
            String id,
            String knowledgeId,
            String sourceType,
            String name,
            Map<String, Object> config,
            String syncStatus,
            Instant lastSyncTime,
            boolean enabled,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Source {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record Document(
            String id,
            String knowledgeId,
            String sourceId,
            String title,
            String path,
            long sizeBytes,
            String language,
            String mimeType,
            String status,
            String content,
            String contentHash,
            Map<String, Object> metadata,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Document {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record Chunk(
            String id,
            String knowledgeId,
            String documentId,
            int chunkNo,
            String content,
            int tokenCount,
            String heading,
            Integer pageNo,
            Map<String, Object> metadata,
            List<String> permissions,
            String contentHash,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
        public Chunk {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    public record RetrieverPolicy(
            String id,
            String knowledgeId,
            int topK,
            String strategy,
            double scoreThreshold,
            double mmrLambda,
            Map<String, Object> metadataFilter,
            double timeWeight,
            String chunkStrategy,
            int chunkSize,
            int chunkOverlap
    ) {
        public RetrieverPolicy {
            metadataFilter = metadataFilter == null ? Map.of() : Map.copyOf(metadataFilter);
        }
    }

    public record SearchHit(
            String chunkId,
            String documentId,
            String documentTitle,
            String content,
            double score,
            int rank,
            String citation
    ) {
    }

    public record SearchResult(
            String knowledgeId,
            String query,
            String strategy,
            List<SearchHit> hits,
            String traceId,
            long latencyMs,
            Instant completedAt
    ) {
    }

    public record Version(
            String id,
            String knowledgeId,
            int version,
            Map<String, Object> snapshot,
            Instant publishedTime,
            Instant createTime,
            String createUser
    ) {
        public Version {
            snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
        }
    }

    public record View(
            Knowledge knowledge,
            List<Source> sources,
            List<Document> documents,
            RetrieverPolicy policy,
            List<String> permissions
    ) {
    }

    public record ExternalDocument(
            String title,
            String path,
            String content,
            String language,
            String mimeType,
            Map<String, Object> metadata
    ) {
    }
}
