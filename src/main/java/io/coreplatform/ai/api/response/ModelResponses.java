package io.coreplatform.ai.api.response;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelAlias;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelRecommendation;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.service.ModelService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelResponses {

    private ModelResponses() {
    }

    public static ModelResponse from(ModelData model) {
        ModelPricing currentPricing = model.currentPricing(Instant.now());
        return new ModelResponse(
                model.id(),
                model.providerId(),
                model.providerCode(),
                model.providerName(),
                model.providerEnabled(),
                model.providerLatencyMs(),
                model.remoteModelId(),
                model.displayName(),
                model.category(),
                model.description(),
                model.status(),
                model.enabled(),
                model.availableFromProvider(),
                model.recommended(),
                model.favorite(),
                model.maxContextTokens(),
                model.maxInputTokens(),
                model.maxOutputTokens(),
                model.defaultMaxTokens(),
                model.contextManuallyOverridden(),
                model.capabilities(),
                model.capabilityOverrides(),
                ParameterResponse.from(model.parameters()),
                currentPricing == null ? null : PricingResponse.from(currentPricing),
                model.pricingHistory().stream().map(PricingResponse::from).toList(),
                model.aliases().stream().map(AliasResponse::from).toList(),
                model.tags(),
                model.lastDiscoveredAt(),
                model.createTime(),
                model.updateTime(),
                model.createUser(),
                model.updateUser()
        );
    }

    public record ModelResponse(
            String id,
            String providerId,
            String providerCode,
            String providerName,
            boolean providerEnabled,
            Long providerLatencyMs,
            String remoteModelId,
            String displayName,
            ModelCategory category,
            String description,
            ModelStatus status,
            boolean enabled,
            boolean availableFromProvider,
            boolean recommended,
            boolean favorite,
            Integer maxContextTokens,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            Integer defaultMaxTokens,
            boolean contextManuallyOverridden,
            Set<Capability> capabilities,
            Map<Capability, Boolean> capabilityOverrides,
            ParameterResponse parameters,
            PricingResponse currentPricing,
            List<PricingResponse> pricingHistory,
            List<AliasResponse> aliases,
            Set<String> tags,
            Instant lastDiscoveredAt,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {
    }

    public record ParameterResponse(
            Double temperature,
            Double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            Integer maxOutputTokens,
            String reasoningEffort,
            Long seed
    ) {

        static ParameterResponse from(ModelParameters parameters) {
            return new ParameterResponse(
                    parameters.temperature(),
                    parameters.topP(),
                    parameters.frequencyPenalty(),
                    parameters.presencePenalty(),
                    parameters.maxOutputTokens(),
                    parameters.reasoningEffort(),
                    parameters.seed()
            );
        }
    }

    public record PricingResponse(
            String id,
            String modelId,
            String currency,
            BigDecimal promptPrice,
            BigDecimal completionPrice,
            BigDecimal cacheReadPrice,
            BigDecimal cacheWritePrice,
            Instant effectiveTime,
            String source,
            String notes,
            Instant createTime,
            String createUser
    ) {

        public static PricingResponse from(ModelPricing pricing) {
            return new PricingResponse(
                    pricing.id(),
                    pricing.modelId(),
                    pricing.currency(),
                    pricing.promptPrice(),
                    pricing.completionPrice(),
                    pricing.cacheReadPrice(),
                    pricing.cacheWritePrice(),
                    pricing.effectiveTime(),
                    pricing.source(),
                    pricing.notes(),
                    pricing.createTime(),
                    pricing.createUser()
            );
        }
    }

    public record AliasResponse(
            String id,
            String alias,
            String modelId,
            String modelDisplayName,
            String providerName,
            String scene,
            int priority,
            boolean enabled,
            Instant createTime,
            Instant updateTime,
            String createUser,
            String updateUser
    ) {

        public static AliasResponse from(ModelAlias alias) {
            return new AliasResponse(
                    alias.id(),
                    alias.alias(),
                    alias.modelId(),
                    alias.modelDisplayName(),
                    alias.providerName(),
                    alias.scene(),
                    alias.priority(),
                    alias.enabled(),
                    alias.createTime(),
                    alias.updateTime(),
                    alias.createUser(),
                    alias.updateUser()
            );
        }
    }

    public record RecommendationResponse(ModelResponse model, double score, String reason) {

        public static RecommendationResponse from(ModelRecommendation recommendation) {
            return new RecommendationResponse(
                    ModelResponses.from(recommendation.model()),
                    recommendation.score(),
                    recommendation.reason()
            );
        }
    }

    public record DefaultResponse(String alias, ModelResponse model) {

        public static DefaultResponse from(ModelService.DefaultModel defaultModel) {
            return new DefaultResponse(
                    defaultModel.alias(),
                    defaultModel.model() == null ? null : ModelResponses.from(defaultModel.model())
            );
        }
    }

    public record AuditResponse(
            String id,
            String action,
            String result,
            String detail,
            String traceId,
            Instant createTime,
            String createUser
    ) {

        public static AuditResponse from(AuditEntry entry) {
            return new AuditResponse(
                    entry.id(),
                    entry.action(),
                    entry.result(),
                    entry.detail(),
                    entry.traceId(),
                    entry.createTime(),
                    entry.createUser()
            );
        }
    }

    public record SyncResponse(int synchronizedModels) {
    }
}
