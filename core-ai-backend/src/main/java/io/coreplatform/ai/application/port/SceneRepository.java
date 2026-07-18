package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneSearchCriteria;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.domain.SceneTemplate;
import io.coreplatform.ai.application.domain.SceneVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SceneRepository {

    boolean existsByCode(String code);

    SceneData insert(SceneData scene, Instant now, String actor);

    SceneData update(SceneData scene, Instant now, String actor);

    Optional<SceneData> findById(String id);

    Optional<SceneData> findByCode(String code);

    List<SceneData> search(SceneSearchCriteria criteria);

    void updateLifecycle(
            String id,
            SceneStatus status,
            boolean enabled,
            int version,
            Instant now,
            String actor
    );

    void markTested(String id, int version, Instant now, String actor);

    boolean versionExists(String sceneId, int version);

    void addVersion(SceneVersion version, Instant now, String actor);

    List<SceneVersion> findVersions(String sceneId);

    Optional<SceneVersion> findVersion(String sceneId, int version);

    int maxVersion(String sceneId);

    List<SceneTemplate> findTemplates();

    Optional<SceneTemplate> findTemplateById(String id);

    SceneTemplate saveTemplate(SceneTemplate template, Instant now, String actor);

    void deleteTemplate(String id);

    void addAudit(AuditEntry entry);

    List<AuditEntry> findAudit(String sceneId);
}
