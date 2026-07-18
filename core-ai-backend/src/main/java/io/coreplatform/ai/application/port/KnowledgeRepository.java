package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.KnowledgeModels.Chunk;
import io.coreplatform.ai.application.domain.KnowledgeModels.Document;
import io.coreplatform.ai.application.domain.KnowledgeModels.Knowledge;
import io.coreplatform.ai.application.domain.KnowledgeModels.RetrieverPolicy;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchResult;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import io.coreplatform.ai.application.domain.KnowledgeModels.Version;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {

    boolean existsByCode(String code);

    List<Knowledge> search(String query);

    Optional<Knowledge> findKnowledge(String id);

    Optional<Knowledge> findKnowledgeByCode(String code);

    void insertKnowledge(Knowledge knowledge);

    void updateKnowledge(Knowledge knowledge);

    Source saveSource(Source source);

    List<Source> findSources(String knowledgeId);

    Document insertDocument(Document document);

    void updateDocument(Document document);

    List<Document> findDocuments(String knowledgeId);

    void replaceChunks(String documentId, List<Chunk> chunks);

    List<Chunk> findChunks(String knowledgeId);

    RetrieverPolicy savePolicy(RetrieverPolicy policy, Instant now, String actor);

    RetrieverPolicy findPolicy(String knowledgeId);

    void replacePermissions(String knowledgeId, List<String> permissions, String actor);

    List<String> findPermissions(String knowledgeId);

    Version insertVersion(Version version);

    List<Version> findVersions(String knowledgeId);

    Optional<Version> findVersion(String knowledgeId, int version);

    void insertSearchLog(SearchResult result, String questionHash, String actor);
}
