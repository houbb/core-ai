package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.ModelAlias;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelSearchCriteria;
import io.coreplatform.ai.application.domain.ModelStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ModelRepository {

    Optional<ModelData> findById(String id);

    List<ModelData> search(ModelSearchCriteria criteria);

    ModelData update(ModelData model, Instant now, String actor);

    void updateStatus(String id, ModelStatus status, Instant now, String actor);

    void updateCapabilities(
            String id,
            Set<Capability> capabilities,
            Map<Capability, Boolean> manualOverrides,
            Instant now,
            String actor
    );

    void updateParameters(String id, ModelParameters parameters, Instant now, String actor);

    ModelPricing addPricing(ModelPricing pricing, Instant now, String actor);

    List<ModelPricing> findPricing(String modelId);

    void setFlags(String id, boolean favorite, boolean recommended, Instant now, String actor);

    void softDelete(String id, Instant now, String actor);

    boolean aliasExists(String alias, String modelId, String excludedId);

    ModelAlias saveAlias(ModelAlias alias, Instant now, String actor);

    void deleteAlias(String id);

    Optional<ModelAlias> findAliasById(String id);

    List<ModelAlias> findAliases(String alias);

    List<ModelData> resolveAlias(String alias);

    void addAudit(AuditEntry entry);

    List<AuditEntry> findAudit(String modelId);
}
