package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;
import io.coreplatform.ai.application.domain.KnowledgeModels.Chunk;
import io.coreplatform.ai.application.domain.KnowledgeModels.Document;
import io.coreplatform.ai.application.domain.KnowledgeModels.ExternalDocument;
import io.coreplatform.ai.application.domain.KnowledgeModels.Knowledge;
import io.coreplatform.ai.application.domain.KnowledgeModels.RetrieverPolicy;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchHit;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchResult;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import io.coreplatform.ai.application.domain.KnowledgeModels.Version;
import io.coreplatform.ai.application.domain.KnowledgeModels.View;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.AnalyticsEventPort;
import io.coreplatform.ai.application.port.KnowledgeRepository;
import io.coreplatform.ai.application.port.KnowledgeSourcePort;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");

    private final KnowledgeRepository repository;
    private final KnowledgeSourcePort sourcePort;
    private final AnalyticsEventPort analytics;
    private final RequestContextPort requestContext;

    public KnowledgeService(
            KnowledgeRepository repository,
            KnowledgeSourcePort sourcePort,
            AnalyticsEventPort analytics,
            RequestContextPort requestContext
    ) {
        this.repository = repository;
        this.sourcePort = sourcePort;
        this.analytics = analytics;
        this.requestContext = requestContext;
    }

    public List<Knowledge> search(String query) {
        return repository.search(query).stream().filter(this::canRead).toList();
    }

    public View get(String id) {
        Knowledge knowledge = requireKnowledge(id);
        requireRead(knowledge);
        return view(knowledge);
    }

    @Transactional
    public View create(
            String code,
            String name,
            String description,
            String category,
            String visibility,
            String projectCode,
            String departmentCode,
            List<String> permissions,
            PolicySpec policySpec
    ) {
        String normalizedCode = normalizeCode(code);
        if (repository.existsByCode(normalizedCode)) {
            throw conflict("AI_KNOWLEDGE_CODE_EXISTS", "Knowledge code already exists");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        String knowledgeId = UUID.randomUUID().toString();
        Knowledge knowledge = new Knowledge(
                knowledgeId,
                normalizedCode,
                required(name, "Knowledge name", 200),
                trim(description, 4000),
                required(category, "Knowledge category", 100).toUpperCase(Locale.ROOT),
                "DRAFT",
                1,
                null,
                normalizeVisibility(visibility),
                trim(projectCode, 100),
                trim(departmentCode, 100),
                actor,
                0,
                now,
                now,
                actor,
                actor
        );
        repository.insertKnowledge(knowledge);
        repository.savePolicy(policy(knowledgeId, policySpec), now, actor);
        repository.replacePermissions(
                knowledgeId,
                normalizePermissions(permissions, knowledge.visibility(), actor),
                actor
        );
        return view(knowledge);
    }

    @Transactional
    public View update(
            String id,
            String name,
            String description,
            String category,
            String visibility,
            String projectCode,
            String departmentCode,
            List<String> permissions,
            PolicySpec policySpec
    ) {
        Knowledge current = requireKnowledge(id);
        requireOwner(current);
        if ("ARCHIVED".equals(current.status())) {
            throw conflict("AI_KNOWLEDGE_ARCHIVED", "Archived Knowledge cannot be edited");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Knowledge updated = new Knowledge(
                current.id(),
                current.code(),
                required(name, "Knowledge name", 200),
                trim(description, 4000),
                required(category, "Knowledge category", 100).toUpperCase(Locale.ROOT),
                "DRAFT",
                current.currentVersion() + 1,
                current.publishedVersion(),
                normalizeVisibility(visibility),
                trim(projectCode, 100),
                trim(departmentCode, 100),
                current.ownerUser(),
                0,
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        repository.updateKnowledge(updated);
        RetrieverPolicy existing = repository.findPolicy(id);
        RetrieverPolicy policy = policy(id, policySpec);
        repository.savePolicy(new RetrieverPolicy(
                existing.id(), id, policy.topK(), policy.strategy(), policy.scoreThreshold(),
                policy.mmrLambda(), policy.metadataFilter(), policy.timeWeight(),
                policy.chunkStrategy(), policy.chunkSize(), policy.chunkOverlap()
        ), now, actor);
        repository.replacePermissions(
                id,
                normalizePermissions(permissions, updated.visibility(), actor),
                actor
        );
        return view(updated);
    }

    @Transactional
    public Source saveSource(
            String knowledgeId,
            String sourceId,
            String sourceType,
            String name,
            Map<String, Object> config,
            boolean enabled
    ) {
        Knowledge knowledge = requireKnowledge(knowledgeId);
        requireOwner(knowledge);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Source current = sourceId == null ? null : repository.findSources(knowledgeId).stream()
                .filter(item -> item.id().equals(sourceId)).findFirst().orElse(null);
        return repository.saveSource(new Source(
                sourceId == null || sourceId.isBlank() ? UUID.randomUUID().toString() : sourceId,
                knowledgeId,
                required(sourceType, "Source type", 40).toUpperCase(Locale.ROOT),
                required(name, "Source name", 200),
                config,
                current == null ? "PENDING" : current.syncStatus(),
                current == null ? null : current.lastSyncTime(),
                enabled,
                current == null ? now : current.createTime(),
                now,
                current == null ? actor : current.createUser(),
                actor
        ));
    }

    @Transactional
    public List<Document> syncSource(String knowledgeId, String sourceId) {
        Knowledge knowledge = requireKnowledge(knowledgeId);
        requireOwner(knowledge);
        Source source = repository.findSources(knowledgeId).stream()
                .filter(item -> item.id().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> notFound("AI_KNOWLEDGE_SOURCE_NOT_FOUND", "Knowledge Source not found"));
        List<ExternalDocument> external = sourcePort.sync(source);
        List<Document> imported = external.stream()
                .map(document -> importDocumentInternal(
                        knowledge,
                        source.id(),
                        document.title(),
                        document.path(),
                        document.content(),
                        document.language(),
                        document.mimeType(),
                        document.metadata(),
                        List.of()
                )).toList();
        repository.saveSource(new Source(
                source.id(), source.knowledgeId(), source.sourceType(), source.name(), source.config(),
                imported.isEmpty() ? "PREVIEW" : "SUCCESS", Instant.now(), source.enabled(),
                source.createTime(), Instant.now(), source.createUser(), requestContext.actor()
        ));
        return imported;
    }

    @Transactional
    public Document importDocument(
            String knowledgeId,
            String sourceId,
            String title,
            String path,
            String content,
            String language,
            String mimeType,
            Map<String, Object> metadata,
            List<String> permissions
    ) {
        Knowledge knowledge = requireKnowledge(knowledgeId);
        requireOwner(knowledge);
        return importDocumentInternal(
                knowledge, sourceId, title, path, content, language, mimeType, metadata, permissions
        );
    }

    @Transactional
    public View process(String knowledgeId) {
        Knowledge current = requireKnowledge(knowledgeId);
        requireOwner(current);
        List<Document> documents = repository.findDocuments(knowledgeId);
        if (documents.isEmpty()) {
            throw conflict("AI_KNOWLEDGE_DOCUMENT_REQUIRED", "At least one Knowledge document is required");
        }
        RetrieverPolicy policy = repository.findPolicy(knowledgeId);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.updateKnowledge(copy(current, "PROCESSING", current.currentVersion(),
                current.publishedVersion(), 10, now, actor));
        int completed = 0;
        for (Document document : documents) {
            List<Chunk> chunks = chunks(document, policy, actor);
            repository.replaceChunks(document.id(), chunks);
            repository.updateDocument(new Document(
                    document.id(), document.knowledgeId(), document.sourceId(), document.title(),
                    document.path(), document.sizeBytes(), document.language(), document.mimeType(),
                    "READY", document.content(), document.contentHash(), document.metadata(),
                    document.createTime(), Instant.now(), document.createUser(), actor
            ));
            completed++;
            repository.updateKnowledge(copy(
                    current,
                    "PROCESSING",
                    current.currentVersion(),
                    current.publishedVersion(),
                    Math.min(95, 10 + completed * 85 / documents.size()),
                    Instant.now(),
                    actor
            ));
        }
        Knowledge ready = copy(
                current, "READY", current.currentVersion(), current.publishedVersion(), 100,
                Instant.now(), actor
        );
        repository.updateKnowledge(ready);
        return view(ready);
    }

    @Transactional
    public View publish(String knowledgeId) {
        Knowledge current = requireKnowledge(knowledgeId);
        requireOwner(current);
        if (!"READY".equals(current.status())) {
            throw conflict("AI_KNOWLEDGE_NOT_READY", "Knowledge must be processed before publishing");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Map<String, Object> snapshot = Map.of(
                "policy", repository.findPolicy(knowledgeId),
                "permissions", repository.findPermissions(knowledgeId),
                "documents", repository.findDocuments(knowledgeId).stream().map(document -> Map.of(
                        "id", document.id(),
                        "title", document.title(),
                        "hash", document.contentHash()
                )).toList()
        );
        repository.insertVersion(new Version(
                UUID.randomUUID().toString(),
                knowledgeId,
                current.currentVersion(),
                snapshot,
                now,
                now,
                actor
        ));
        Knowledge published = copy(
                current, "PUBLISHED", current.currentVersion(), current.currentVersion(), 100, now, actor
        );
        repository.updateKnowledge(published);
        return view(published);
    }

    @Transactional
    public View archive(String knowledgeId) {
        Knowledge current = requireKnowledge(knowledgeId);
        requireOwner(current);
        Knowledge archived = copy(
                current, "ARCHIVED", current.currentVersion(), null, current.progress(),
                Instant.now(), requestContext.actor()
        );
        repository.updateKnowledge(archived);
        return view(archived);
    }

    public List<Version> versions(String knowledgeId) {
        Knowledge knowledge = requireKnowledge(knowledgeId);
        requireRead(knowledge);
        return repository.findVersions(knowledgeId);
    }

    @Transactional
    public View rollback(String knowledgeId, int version) {
        Knowledge current = requireKnowledge(knowledgeId);
        requireOwner(current);
        Version source = repository.findVersion(knowledgeId, version)
                .orElseThrow(() -> notFound("AI_KNOWLEDGE_VERSION_NOT_FOUND", "Knowledge version not found"));
        int next = repository.findVersions(knowledgeId).stream()
                .mapToInt(Version::version).max().orElse(current.currentVersion()) + 1;
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.insertVersion(new Version(
                UUID.randomUUID().toString(),
                knowledgeId,
                next,
                source.snapshot(),
                null,
                now,
                actor
        ));
        Knowledge rolledBack = copy(current, "READY", next, current.publishedVersion(), 100, now, actor);
        repository.updateKnowledge(rolledBack);
        return view(rolledBack);
    }

    @Transactional
    public SearchResult retrieve(String reference, String query, Integer topK) {
        Knowledge knowledge = repository.findKnowledge(reference)
                .or(() -> repository.findKnowledgeByCode(reference))
                .orElseThrow(() -> notFound("AI_KNOWLEDGE_NOT_FOUND", "Knowledge not found"));
        requireRead(knowledge);
        if (!"PUBLISHED".equals(knowledge.status()) || knowledge.publishedVersion() == null) {
            throw conflict("AI_KNOWLEDGE_NOT_PUBLISHED", "Knowledge is not Published");
        }
        String question = required(query, "Search query", 100_000);
        RetrieverPolicy policy = repository.findPolicy(knowledge.id());
        int limit = topK == null ? policy.topK() : Math.max(1, Math.min(topK, 50));
        long started = System.nanoTime();
        Map<String, Document> documents = repository.findDocuments(knowledge.id()).stream()
                .collect(java.util.stream.Collectors.toMap(Document::id, item -> item));
        List<ScoredChunk> scored = repository.findChunks(knowledge.id()).stream()
                .filter(this::canReadChunk)
                .map(chunk -> new ScoredChunk(chunk, score(question, chunk.content())))
                .filter(item -> item.score() > 0 && item.score() >= policy.scoreThreshold())
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(item -> item.chunk().documentId())
                        .thenComparingInt(item -> item.chunk().chunkNo()))
                .limit(limit)
                .toList();
        List<SearchHit> hits = new ArrayList<>();
        int rank = 1;
        for (ScoredChunk item : scored) {
            Document document = documents.get(item.chunk().documentId());
            hits.add(new SearchHit(
                    item.chunk().id(),
                    item.chunk().documentId(),
                    document == null ? "Document" : document.title(),
                    item.chunk().content(),
                    item.score(),
                    rank,
                    "[" + rank++ + "] " + (document == null ? "Document" : document.title())
            ));
        }
        long latency = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
        SearchResult result = new SearchResult(
                knowledge.id(),
                question,
                policy.strategy(),
                hits,
                requestContext.traceId(),
                latency,
                Instant.now()
        );
        repository.insertSearchLog(result, hash(question), requestContext.actor());
        analytics.record(new UsageEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                result.traceId(),
                "KNOWLEDGE_SEARCH",
                "KNOWLEDGE",
                knowledge.id(),
                requestContext.actor(),
                null,
                null,
                null,
                null,
                null,
                Math.max(1, question.length() / 4L),
                hits.stream().mapToLong(hit -> Math.max(1, hit.content().length() / 4L)).sum(),
                0,
                BigDecimal.ZERO,
                null,
                latency,
                "SUCCESS",
                null,
                Map.of("strategy", policy.strategy(), "resultCount", hits.size()),
                result.completedAt(),
                requestContext.actor()
        ));
        return result;
    }

    private Document importDocumentInternal(
            Knowledge knowledge,
            String sourceId,
            String title,
            String path,
            String content,
            String language,
            String mimeType,
            Map<String, Object> metadata,
            List<String> permissions
    ) {
        String body = required(content, "Document content", 5_000_000);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        enriched.put("permissions", normalizePermissions(permissions, knowledge.visibility(), actor));
        Document document = new Document(
                UUID.randomUUID().toString(),
                knowledge.id(),
                trim(sourceId, 64),
                required(title, "Document title", 500),
                trim(path, 2000),
                body.getBytes(StandardCharsets.UTF_8).length,
                trim(language, 40),
                mimeType == null || mimeType.isBlank() ? "text/plain" : trim(mimeType, 200),
                "PENDING",
                body,
                hash(body),
                enriched,
                now,
                now,
                actor,
                actor
        );
        return repository.insertDocument(document);
    }

    private List<Chunk> chunks(Document document, RetrieverPolicy policy, String actor) {
        List<String> pieces = split(document.content(), policy);
        @SuppressWarnings("unchecked")
        List<String> permissions = document.metadata().get("permissions") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        Instant now = Instant.now();
        List<Chunk> result = new ArrayList<>();
        for (int index = 0; index < pieces.size(); index++) {
            String content = pieces.get(index);
            result.add(new Chunk(
                    UUID.randomUUID().toString(),
                    document.knowledgeId(),
                    document.id(),
                    index + 1,
                    content,
                    Math.max(1, content.length() / 4),
                    heading(content),
                    null,
                    Map.of("sourcePath", document.path() == null ? "" : document.path()),
                    permissions,
                    hash(content),
                    now,
                    now,
                    actor,
                    actor
            ));
        }
        return result;
    }

    private List<String> split(String content, RetrieverPolicy policy) {
        if ("PARAGRAPH".equals(policy.chunkStrategy())) {
            List<String> paragraphs = Pattern.compile("\\R\\s*\\R")
                    .splitAsStream(content)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (!paragraphs.isEmpty()) {
                return paragraphs;
            }
        }
        int size = Math.max(64, policy.chunkSize() * 4);
        int overlap = Math.min(size - 1, Math.max(0, policy.chunkOverlap() * 4));
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += size - overlap) {
            int end = Math.min(content.length(), start + size);
            chunks.add(content.substring(start, end).trim());
            if (end == content.length()) {
                break;
            }
        }
        return chunks.stream().filter(value -> !value.isBlank()).toList();
    }

    private double score(String query, String content) {
        String normalizedQuery = normalizeText(query);
        String normalizedContent = normalizeText(content);
        if (normalizedQuery.isBlank()) {
            return 0;
        }
        Set<String> terms = new HashSet<>(List.of(normalizedQuery.split(" ")));
        terms.remove("");
        long matches = terms.stream().filter(normalizedContent::contains).count();
        double termScore = terms.isEmpty() ? 0 : (double) matches / terms.size();
        double phraseScore = normalizedContent.contains(normalizedQuery) ? 1 : 0;
        return Math.min(1, phraseScore * 0.6 + termScore * 0.4);
    }

    private boolean canReadChunk(Chunk chunk) {
        if (chunk.permissions().isEmpty()) {
            return true;
        }
        String actor = requestContext.actor();
        return chunk.permissions().contains("EVERYONE:*")
                || chunk.permissions().contains("USER:" + actor);
    }

    private boolean canRead(Knowledge knowledge) {
        if (knowledge.ownerUser().equals(requestContext.actor()) || "PUBLIC".equals(knowledge.visibility())) {
            return true;
        }
        List<String> permissions = repository.findPermissions(knowledge.id());
        return permissions.contains("EVERYONE:*")
                || permissions.contains("USER:" + requestContext.actor());
    }

    private void requireRead(Knowledge knowledge) {
        if (!canRead(knowledge)) {
            throw new ProviderOperationException(
                    "AI_KNOWLEDGE_FORBIDDEN", "Knowledge is not accessible", 403
            );
        }
    }

    private void requireOwner(Knowledge knowledge) {
        if (!knowledge.ownerUser().equals(requestContext.actor())) {
            throw new ProviderOperationException(
                    "AI_KNOWLEDGE_MANAGE_FORBIDDEN", "Knowledge cannot be managed by current User", 403
            );
        }
    }

    private RetrieverPolicy policy(String knowledgeId, PolicySpec value) {
        PolicySpec spec = value == null
                ? new PolicySpec(5, "HYBRID", 0, 0.5, Map.of(), 0, "PARAGRAPH", 512, 64)
                : value;
        if (spec.topK() < 1 || spec.topK() > 50 || spec.scoreThreshold() < 0
                || spec.scoreThreshold() > 1 || spec.mmrLambda() < 0 || spec.mmrLambda() > 1
                || spec.chunkSize() < 32 || spec.chunkSize() > 8192
                || spec.chunkOverlap() < 0 || spec.chunkOverlap() >= spec.chunkSize()) {
            throw invalid("AI_KNOWLEDGE_POLICY_INVALID", "Knowledge retrieval or chunk policy is invalid");
        }
        return new RetrieverPolicy(
                UUID.randomUUID().toString(),
                knowledgeId,
                spec.topK(),
                spec.strategy() == null || spec.strategy().isBlank()
                        ? "HYBRID" : spec.strategy().toUpperCase(Locale.ROOT),
                spec.scoreThreshold(),
                spec.mmrLambda(),
                spec.metadataFilter(),
                spec.timeWeight(),
                spec.chunkStrategy() == null || spec.chunkStrategy().isBlank()
                        ? "PARAGRAPH" : spec.chunkStrategy().toUpperCase(Locale.ROOT),
                spec.chunkSize(),
                spec.chunkOverlap()
        );
    }

    private View view(Knowledge knowledge) {
        return new View(
                knowledge,
                repository.findSources(knowledge.id()),
                repository.findDocuments(knowledge.id()),
                repository.findPolicy(knowledge.id()),
                repository.findPermissions(knowledge.id())
        );
    }

    private Knowledge requireKnowledge(String id) {
        return repository.findKnowledge(id)
                .orElseThrow(() -> notFound("AI_KNOWLEDGE_NOT_FOUND", "Knowledge not found"));
    }

    private Knowledge copy(
            Knowledge value,
            String status,
            int currentVersion,
            Integer publishedVersion,
            int progress,
            Instant now,
            String actor
    ) {
        return new Knowledge(
                value.id(), value.code(), value.name(), value.description(), value.category(),
                status, currentVersion, publishedVersion, value.visibility(), value.projectCode(),
                value.departmentCode(), value.ownerUser(), progress, value.createTime(), now,
                value.createUser(), actor
        );
    }

    private List<String> normalizePermissions(
            List<String> permissions,
            String visibility,
            String actor
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if ("PUBLIC".equals(visibility)) {
            result.add("EVERYONE:*");
        }
        result.add("USER:" + actor);
        if (permissions != null) {
            permissions.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(item -> item.contains(":") ? item : "USER:" + item)
                    .limit(100)
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private String normalizeVisibility(String value) {
        String visibility = value == null || value.isBlank()
                ? "PUBLIC" : value.toUpperCase(Locale.ROOT);
        if (!List.of("PUBLIC", "PROJECT", "DEPARTMENT", "PRIVATE").contains(visibility)) {
            throw invalid("AI_KNOWLEDGE_VISIBILITY_INVALID", "Unsupported Knowledge visibility");
        }
        return visibility;
    }

    private String normalizeCode(String code) {
        String normalized = required(code, "Knowledge code", 100).toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw invalid("AI_KNOWLEDGE_CODE_INVALID", "Knowledge code format is invalid");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String heading(String value) {
        String first = value.lines().findFirst().orElse("").trim();
        return first.length() <= 120 ? first : first.substring(0, 120);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw invalid("AI_KNOWLEDGE_FIELD_REQUIRED", label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw invalid("AI_KNOWLEDGE_FIELD_TOO_LONG", label + " exceeds " + max + " characters");
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

    private record ScoredChunk(Chunk chunk, double score) {
    }

    public record PolicySpec(
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
        public PolicySpec {
            metadataFilter = metadataFilter == null ? Map.of() : Map.copyOf(metadataFilter);
        }
    }
}
