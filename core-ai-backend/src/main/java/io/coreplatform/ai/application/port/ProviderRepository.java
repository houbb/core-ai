package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderModel;
import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.domain.ProviderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProviderRepository {

    boolean existsByCode(String code, String excludedId);

    ProviderData insert(ProviderData provider);

    ProviderData update(ProviderData provider);

    Optional<ProviderData> findById(String id);

    List<ProviderData> search(ProviderSearchCriteria criteria);

    void updateStatus(String providerId, ProviderStatus status, boolean enabled, Instant now, String actor);

    void updateCapability(String providerId, Set<Capability> capabilities, Instant now, String actor);

    void recordHealthSuccess(String providerId, long latencyMs, Instant now, String actor);

    void recordHealthFailure(
            String providerId,
            long latencyMs,
            Integer statusCode,
            String message,
            Instant now,
            String actor
    );

    void syncModels(String providerId, List<DiscoveredModel> models, Instant now, String actor);

    List<ProviderModel> findModels(String providerId);

    void softDelete(String providerId, Instant now, String actor);

    void addAudit(AuditEntry entry);

    List<AuditEntry> findAudit(String providerId);
}
