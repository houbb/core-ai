package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ModelAlias;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelPricing;
import io.coreplatform.ai.application.domain.ModelRecommendation;
import io.coreplatform.ai.application.domain.ModelSearchCriteria;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.domain.ProviderData;
import io.coreplatform.ai.application.domain.ProviderModel;
import io.coreplatform.ai.application.domain.ProviderSearchCriteria;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ModelDiscoveryPort;
import io.coreplatform.ai.application.port.ModelRepository;
import io.coreplatform.ai.application.port.ProviderRepository;
import io.coreplatform.ai.application.port.RequestContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ModelService {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,99}");
    private static final List<String> DEFAULT_ALIASES = List.of(
            "chat-default",
            "coding-default",
            "embedding-default",
            "vision-default",
            "ocr-default",
            "reasoning-default",
            "image-default",
            "video-default",
            "audio-default",
            "speech-default",
            "moderation-default",
            "rerank-default"
    );

    private final ModelRepository repository;
    private final ModelDiscoveryPort discoveryPort;
    private final ProviderRepository providerRepository;
    private final RequestContextPort requestContext;

    public ModelService(
            ModelRepository repository,
            ModelDiscoveryPort discoveryPort,
            ProviderRepository providerRepository,
            RequestContextPort requestContext
    ) {
        this.repository = repository;
        this.discoveryPort = discoveryPort;
        this.providerRepository = providerRepository;
        this.requestContext = requestContext;
    }

    public List<ModelData> search(ModelSearchCriteria criteria) {
        return repository.search(criteria);
    }

    public ModelData get(String id) {
        return requireModel(id);
    }

    @Transactional
    public ModelData update(String id, UpdateModelCommand command) {
        ModelData current = requireModel(id);
        validateBasic(command);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        ModelData next = new ModelData(
                current.id(),
                current.providerId(),
                current.providerCode(),
                current.providerName(),
                current.providerEnabled(),
                current.providerLatencyMs(),
                current.remoteModelId(),
                command.displayName().trim(),
                command.category(),
                trimToNull(command.description()),
                current.status(),
                current.enabled(),
                current.availableFromProvider(),
                current.recommended(),
                current.favorite(),
                command.maxContextTokens(),
                command.maxInputTokens(),
                command.maxOutputTokens(),
                command.defaultMaxTokens(),
                command.contextManuallyOverridden(),
                current.capabilities(),
                current.capabilityOverrides(),
                current.parameters(),
                current.pricingHistory(),
                current.aliases(),
                normalizeTags(command.tags()),
                current.lastDiscoveredAt(),
                current.createTime(),
                now,
                current.createUser(),
                actor
        );
        ModelData saved = repository.update(next, now, actor);
        audit(id, "UPDATE", "{\"category\":\"" + saved.category() + "\"}", actor, now);
        return saved;
    }

    @Transactional
    public ModelData updateCapabilities(String id, Map<Capability, Boolean> overrides) {
        ModelData current = requireModel(id);
        EnumMap<Capability, Boolean> normalizedOverrides = new EnumMap<>(Capability.class);
        if (overrides != null) {
            if (overrides.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
                throw unprocessable(
                        "AI_MODEL_CAPABILITY_OVERRIDE_INVALID",
                        "Capability override names and values are required"
                );
            }
            normalizedOverrides.putAll(overrides);
        }
        Set<Capability> capabilities = new LinkedHashSet<>(current.capabilities());
        normalizedOverrides.forEach((capability, enabled) -> {
            if (Boolean.TRUE.equals(enabled)) {
                capabilities.add(capability);
            } else {
                capabilities.remove(capability);
            }
        });
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.updateCapabilities(id, capabilities, normalizedOverrides, now, actor);
        audit(id, "UPDATE_CAPABILITY", "{\"overrides\":" + normalizedOverrides.size() + "}", actor, now);
        return requireModel(id);
    }

    @Transactional
    public ModelData resetCapabilities(String id) {
        ModelData current = requireModel(id);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.updateCapabilities(id, current.capabilities(), Map.of(), now, actor);
        synchronizeProvider(current.providerId());
        audit(id, "RESET_CAPABILITY", "{\"source\":\"provider\"}", actor, now);
        return requireModel(id);
    }

    @Transactional
    public ModelData updateParameters(String id, ModelParameters parameters) {
        ModelData current = requireModel(id);
        validateParameters(parameters, current.maxOutputTokens());
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.updateParameters(id, parameters, now, actor);
        audit(id, "UPDATE_PARAMETER", "{}", actor, now);
        return requireModel(id);
    }

    @Transactional
    public ModelPricing addPricing(String id, PricingCommand command) {
        requireModel(id);
        validatePricing(command);
        if (repository.findPricing(id).stream()
                .anyMatch(price -> price.effectiveTime().equals(command.effectiveTime()))) {
            throw conflict("AI_MODEL_PRICE_EFFECTIVE_TIME_EXISTS", "A price already exists at this effective time");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        ModelPricing pricing = repository.addPricing(new ModelPricing(
                UUID.randomUUID().toString(),
                id,
                command.currency().trim().toUpperCase(Locale.ROOT),
                command.promptPrice(),
                command.completionPrice(),
                command.cacheReadPrice(),
                command.cacheWritePrice(),
                command.effectiveTime(),
                "MANUAL",
                trimToNull(command.notes()),
                now,
                actor
        ), now, actor);
        audit(id, "ADD_PRICING", "{\"currency\":\"" + pricing.currency() + "\"}", actor, now);
        return pricing;
    }

    @Transactional
    public ModelData transition(String id, ModelStatus target) {
        ModelData current = requireModel(id);
        validateTransition(current, target);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.updateStatus(id, target, now, actor);
        audit(
                id,
                "STATUS_CHANGE",
                "{\"from\":\"" + current.status() + "\",\"to\":\"" + target + "\"}",
                actor,
                now
        );
        return requireModel(id);
    }

    @Transactional
    public ModelData setFlags(String id, boolean favorite, boolean recommended) {
        requireModel(id);
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.setFlags(id, favorite, recommended, now, actor);
        audit(
                id,
                "UPDATE_FLAGS",
                "{\"favorite\":" + favorite + ",\"recommended\":" + recommended + "}",
                actor,
                now
        );
        return requireModel(id);
    }

    @Transactional
    public void delete(String id) {
        ModelData current = requireModel(id);
        if (current.status() == ModelStatus.ENABLED) {
            throw conflict("AI_MODEL_DISABLE_BEFORE_DELETE", "Disable the model before deleting it");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        repository.softDelete(id, now, actor);
        audit(id, "DELETE", "{\"softDelete\":true}", actor, now);
    }

    public int synchronize(String providerId) {
        if (providerId != null && !providerId.isBlank()) {
            return synchronizeProvider(providerId);
        }
        int synchronizedCount = 0;
        for (ProviderData provider : providerRepository.search(
                new ProviderSearchCriteria(null, null, null, null, null)
        )) {
            synchronizedCount += synchronizeProvider(provider.id());
        }
        return synchronizedCount;
    }

    public List<ModelData> compare(List<String> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 5) {
            throw unprocessable("AI_MODEL_COMPARE_SIZE_INVALID", "Compare between 2 and 5 models");
        }
        List<String> uniqueIds = ids.stream().distinct().toList();
        if (uniqueIds.size() != ids.size()) {
            throw unprocessable("AI_MODEL_COMPARE_DUPLICATE", "Comparison models must be unique");
        }
        return uniqueIds.stream().map(this::requireModel).toList();
    }

    public List<ModelRecommendation> recommend(
            Capability capability,
            RecommendationMode mode,
            int limit
    ) {
        if (limit < 1 || limit > 20) {
            throw unprocessable("AI_MODEL_RECOMMEND_LIMIT_INVALID", "Recommendation limit must be 1-20");
        }
        List<ModelData> candidates = repository.search(new ModelSearchCriteria(
                null,
                null,
                null,
                capability,
                ModelStatus.ENABLED,
                true,
                null,
                null,
                true,
                null,
                null
        )).stream().filter(ModelData::providerEnabled).toList();
        Comparator<ModelRecommendation> comparator = Comparator.comparingDouble(ModelRecommendation::score).reversed();
        return candidates.stream()
                .map(model -> score(model, mode))
                .sorted(comparator)
                .limit(limit)
                .toList();
    }

    @Transactional
    public ModelAlias saveAlias(String id, AliasCommand command) {
        String aliasCode = normalizeAlias(command.alias());
        ModelData model = requireModel(command.modelId());
        if (repository.aliasExists(aliasCode, model.id(), id)) {
            throw conflict("AI_MODEL_ALIAS_EXISTS", "Alias is already bound to this model");
        }
        if (command.priority() < 0 || command.priority() > 10000) {
            throw unprocessable("AI_MODEL_ALIAS_PRIORITY_INVALID", "Alias priority must be 0-10000");
        }
        String actor = requestContext.actor();
        Instant now = Instant.now();
        ModelAlias current = id == null ? null : repository.findAliasById(id)
                .orElseThrow(() -> notFound("AI_MODEL_ALIAS_NOT_FOUND", "Alias binding not found"));
        ModelAlias saved = repository.saveAlias(new ModelAlias(
                id == null ? UUID.randomUUID().toString() : id,
                aliasCode,
                model.id(),
                model.displayName(),
                model.providerName(),
                trimToNull(command.scene()),
                command.priority(),
                command.enabled(),
                current == null ? now : current.createTime(),
                now,
                current == null ? actor : current.createUser(),
                actor
        ), now, actor);
        audit(
                model.id(),
                id == null ? "CREATE_ALIAS" : "UPDATE_ALIAS",
                "{\"alias\":\"" + safeJson(aliasCode) + "\"}",
                actor,
                now
        );
        return saved;
    }

    @Transactional
    public void deleteAlias(String id) {
        ModelAlias alias = repository.findAliasById(id)
                .orElseThrow(() -> notFound("AI_MODEL_ALIAS_NOT_FOUND", "Alias binding not found"));
        repository.deleteAlias(id);
        audit(
                alias.modelId(),
                "DELETE_ALIAS",
                "{\"alias\":\"" + safeJson(alias.alias()) + "\"}",
                requestContext.actor(),
                Instant.now()
        );
    }

    public List<ModelAlias> aliases(String alias) {
        return repository.findAliases(alias == null ? null : normalizeAlias(alias));
    }

    public List<ModelData> resolveAlias(String alias) {
        String aliasCode = normalizeAlias(alias);
        List<ModelData> result = repository.resolveAlias(aliasCode);
        if (result.isEmpty()) {
            throw notFound("AI_MODEL_ALIAS_UNRESOLVED", "No enabled model can resolve this alias");
        }
        return result;
    }

    public List<DefaultModel> defaults() {
        return DEFAULT_ALIASES.stream()
                .map(alias -> new DefaultModel(
                        alias,
                        repository.resolveAlias(alias).stream().findFirst().orElse(null)
                ))
                .toList();
    }

    public List<AuditEntry> audit(String id) {
        requireModel(id);
        return repository.findAudit(id);
    }

    private int synchronizeProvider(String providerId) {
        ProviderData provider = providerRepository.findById(providerId)
                .orElseThrow(() -> notFound("AI_PROVIDER_NOT_FOUND", "Provider not found"));
        List<ProviderModel> cache = providerRepository.findModels(providerId).stream()
                .filter(model -> "ACTIVE".equals(model.status()))
                .toList();
        List<DiscoveredModel> models = cache.stream()
                .map(model -> new DiscoveredModel(
                        model.modelId(),
                        model.displayName(),
                        model.capabilities(),
                        model.contextLength()
                ))
                .toList();
        discoveryPort.synchronizeDiscovered(provider.id(), models, Instant.now(), requestContext.actor());
        return models.size();
    }

    private void validateBasic(UpdateModelCommand command) {
        if (command.displayName() == null || command.displayName().isBlank()
                || command.displayName().trim().length() > 300) {
            throw unprocessable("AI_MODEL_DISPLAY_NAME_INVALID", "Display name is required and max 300 chars");
        }
        if (command.category() == null) {
            throw unprocessable("AI_MODEL_CATEGORY_REQUIRED", "Model category is required");
        }
        validateTokenValue(command.maxContextTokens(), "maxContextTokens");
        validateTokenValue(command.maxInputTokens(), "maxInputTokens");
        validateTokenValue(command.maxOutputTokens(), "maxOutputTokens");
        validateTokenValue(command.defaultMaxTokens(), "defaultMaxTokens");
        validateWithinContext(command.maxInputTokens(), command.maxContextTokens(), "maxInputTokens");
        validateWithinContext(command.maxOutputTokens(), command.maxContextTokens(), "maxOutputTokens");
        if (command.defaultMaxTokens() != null && command.maxOutputTokens() != null
                && command.defaultMaxTokens() > command.maxOutputTokens()) {
            throw unprocessable(
                    "AI_MODEL_DEFAULT_TOKENS_INVALID",
                    "Default max tokens cannot exceed max output tokens"
            );
        }
    }

    private void validateWithinContext(Integer value, Integer maxContextTokens, String field) {
        if (value != null && maxContextTokens != null && value > maxContextTokens) {
            throw unprocessable(
                    "AI_MODEL_CONTEXT_LIMIT_INVALID",
                    field + " cannot exceed maxContextTokens"
            );
        }
    }

    private void validateTokenValue(Integer value, String field) {
        if (value != null && (value < 1 || value > 10_000_000)) {
            throw unprocessable("AI_MODEL_CONTEXT_INVALID", field + " must be between 1 and 10000000");
        }
    }

    private void validateParameters(ModelParameters parameters, Integer modelMaxOutputTokens) {
        validateRange(parameters.temperature(), 0, 2, "temperature");
        validateRange(parameters.topP(), 0, 1, "topP");
        validateRange(parameters.frequencyPenalty(), -2, 2, "frequencyPenalty");
        validateRange(parameters.presencePenalty(), -2, 2, "presencePenalty");
        validateTokenValue(parameters.maxOutputTokens(), "maxOutputTokens");
        if (parameters.maxOutputTokens() != null && modelMaxOutputTokens != null
                && parameters.maxOutputTokens() > modelMaxOutputTokens) {
            throw unprocessable(
                    "AI_MODEL_PARAMETER_MAX_TOKENS_INVALID",
                    "Parameter max output tokens cannot exceed the model max output tokens"
            );
        }
        if (parameters.reasoningEffort() != null
                && !Set.of("low", "medium", "high").contains(
                parameters.reasoningEffort().toLowerCase(Locale.ROOT)
        )) {
            throw unprocessable(
                    "AI_MODEL_REASONING_EFFORT_INVALID",
                    "Reasoning effort must be low, medium or high"
            );
        }
    }

    private void validateRange(Double value, double minimum, double maximum, String field) {
        if (value != null && (value < minimum || value > maximum)) {
            throw unprocessable(
                    "AI_MODEL_PARAMETER_INVALID",
                    field + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private void validatePricing(PricingCommand command) {
        if (command.currency() == null || command.currency().isBlank()
                || command.currency().trim().length() > 16) {
            throw unprocessable("AI_MODEL_PRICE_CURRENCY_INVALID", "Currency is required and max 16 chars");
        }
        if (command.effectiveTime() == null) {
            throw unprocessable("AI_MODEL_PRICE_EFFECTIVE_TIME_REQUIRED", "Effective time is required");
        }
        if (command.promptPrice() == null
                && command.completionPrice() == null
                && command.cacheReadPrice() == null
                && command.cacheWritePrice() == null) {
            throw unprocessable("AI_MODEL_PRICE_REQUIRED", "At least one price value is required");
        }
        List<BigDecimal> values = List.of(
                zeroIfNull(command.promptPrice()),
                zeroIfNull(command.completionPrice()),
                zeroIfNull(command.cacheReadPrice()),
                zeroIfNull(command.cacheWritePrice())
        );
        if (values.stream().anyMatch(value -> value.signum() < 0)) {
            throw unprocessable("AI_MODEL_PRICE_INVALID", "Prices cannot be negative");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateTransition(ModelData current, ModelStatus target) {
        if (target == null || target == ModelStatus.DELETED) {
            throw unprocessable("AI_MODEL_STATUS_INVALID", "Use the delete endpoint for logical deletion");
        }
        boolean allowed = switch (current.status()) {
            case DISCOVERED -> target == ModelStatus.REGISTERED;
            case REGISTERED -> target == ModelStatus.ENABLED || target == ModelStatus.DISABLED;
            case ENABLED -> target == ModelStatus.DEPRECATED || target == ModelStatus.DISABLED;
            case DEPRECATED -> target == ModelStatus.ENABLED || target == ModelStatus.DISABLED;
            case DISABLED -> target == ModelStatus.ENABLED || target == ModelStatus.DEPRECATED;
            case DELETED -> false;
        };
        if (!allowed) {
            throw conflict(
                    "AI_MODEL_STATUS_TRANSITION_INVALID",
                    "Cannot transition model from " + current.status() + " to " + target
            );
        }
        if (target == ModelStatus.ENABLED
                && (!current.providerEnabled() || !current.availableFromProvider())) {
            throw conflict(
                    "AI_MODEL_PROVIDER_UNAVAILABLE",
                    "Provider must be enabled and the model must be available before enabling"
            );
        }
    }

    private ModelRecommendation score(ModelData model, RecommendationMode mode) {
        Instant now = Instant.now();
        ModelPricing price = model.currentPricing(now);
        double totalPrice = price == null
                ? Double.MAX_VALUE
                : decimal(price.promptPrice()) + decimal(price.completionPrice());
        long latency = model.providerLatencyMs() == null ? Long.MAX_VALUE : model.providerLatencyMs();
        long context = model.maxContextTokens() == null ? 0 : model.maxContextTokens();
        return switch (mode) {
            case CHEAPEST -> new ModelRecommendation(
                    model,
                    totalPrice == Double.MAX_VALUE ? -Double.MAX_VALUE : -totalPrice,
                    price == null
                            ? "No active pricing"
                            : price.currency() + " " + format(totalPrice) + " / 1M input+output tokens"
            );
            case FASTEST -> new ModelRecommendation(
                    model,
                    latency == Long.MAX_VALUE ? -Double.MAX_VALUE : -latency,
                    latency == Long.MAX_VALUE ? "No latency data" : latency + " ms provider latency"
            );
            case LARGEST_CONTEXT -> new ModelRecommendation(
                    model,
                    context,
                    context + " context tokens"
            );
            case BEST -> {
                double score = (model.recommended() ? 50 : 0)
                        + (model.favorite() ? 20 : 0)
                        + model.capabilities().size() * 3
                        + Math.log10(Math.max(1, context)) * 4
                        - (latency == Long.MAX_VALUE ? 10 : Math.min(20, latency / 100.0))
                        - (totalPrice == Double.MAX_VALUE ? 10 : Math.min(20, totalPrice / 2));
                yield new ModelRecommendation(
                        model,
                        score,
                        "Quality score from recommendation, favorite, capability, context, latency and price"
                );
            }
        };
    }

    private double decimal(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private ModelData requireModel(String id) {
        return repository.findById(id)
                .orElseThrow(() -> notFound("AI_MODEL_NOT_FOUND", "Model not found"));
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                String value = tag.trim().toLowerCase(Locale.ROOT);
                if (value.length() > 100) {
                    throw unprocessable("AI_MODEL_TAG_INVALID", "Tag max length is 100");
                }
                normalized.add(value);
            }
        }
        return Set.copyOf(normalized);
    }

    private String normalizeAlias(String alias) {
        String normalized = alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
        if (!ALIAS_PATTERN.matcher(normalized).matches()) {
            throw unprocessable(
                    "AI_MODEL_ALIAS_INVALID",
                    "Alias must contain 2-100 lowercase letters, numbers, dots, underscores or hyphens"
            );
        }
        return normalized;
    }

    private void audit(
            String modelId,
            String action,
            String detail,
            String actor,
            Instant now
    ) {
        repository.addAudit(new AuditEntry(
                UUID.randomUUID().toString(),
                "AI_MODEL",
                modelId,
                action,
                "SUCCESS",
                detail,
                requestContext.traceId(),
                now,
                actor
        ));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ProviderOperationException notFound(String code, String message) {
        return new ProviderOperationException(code, message, 404);
    }

    private ProviderOperationException conflict(String code, String message) {
        return new ProviderOperationException(code, message, 409);
    }

    private ProviderOperationException unprocessable(String code, String message) {
        return new ProviderOperationException(code, message, 422);
    }

    public enum RecommendationMode {
        BEST,
        CHEAPEST,
        FASTEST,
        LARGEST_CONTEXT
    }

    public record UpdateModelCommand(
            String displayName,
            ModelCategory category,
            String description,
            Integer maxContextTokens,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            Integer defaultMaxTokens,
            boolean contextManuallyOverridden,
            Set<String> tags
    ) {
    }

    public record PricingCommand(
            String currency,
            BigDecimal promptPrice,
            BigDecimal completionPrice,
            BigDecimal cacheReadPrice,
            BigDecimal cacheWritePrice,
            Instant effectiveTime,
            String notes
    ) {
    }

    public record AliasCommand(
            String alias,
            String modelId,
            String scene,
            int priority,
            boolean enabled
    ) {
    }

    public record DefaultModel(String alias, ModelData model) {
    }
}
