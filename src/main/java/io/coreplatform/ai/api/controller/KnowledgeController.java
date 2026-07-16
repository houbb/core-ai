package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.KnowledgeModels.Document;
import io.coreplatform.ai.application.domain.KnowledgeModels.Knowledge;
import io.coreplatform.ai.application.domain.KnowledgeModels.SearchResult;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import io.coreplatform.ai.application.domain.KnowledgeModels.Version;
import io.coreplatform.ai.application.domain.KnowledgeModels.View;
import io.coreplatform.ai.application.service.KnowledgeService;
import io.coreplatform.ai.application.service.KnowledgeService.PolicySpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/ai/admin/knowledge")
    public List<Knowledge> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    @PostMapping("/api/v1/ai/admin/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody KnowledgeRequest request) {
        return service.create(
                request.code(), request.name(), request.description(), request.category(),
                request.visibility(), request.projectCode(), request.departmentCode(),
                request.permissions(), policy(request.policy())
        );
    }

    @GetMapping("/api/v1/ai/admin/knowledge/{id}")
    public View get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/ai/admin/knowledge/{id}")
    public View update(@PathVariable String id, @Valid @RequestBody KnowledgeRequest request) {
        return service.update(
                id, request.name(), request.description(), request.category(),
                request.visibility(), request.projectCode(), request.departmentCode(),
                request.permissions(), policy(request.policy())
        );
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public Source createSource(@PathVariable String id, @Valid @RequestBody SourceRequest request) {
        return service.saveSource(
                id, null, request.sourceType(), request.name(), request.config(), request.enabled()
        );
    }

    @PutMapping("/api/v1/ai/admin/knowledge/{id}/sources/{sourceId}")
    public Source updateSource(
            @PathVariable String id,
            @PathVariable String sourceId,
            @Valid @RequestBody SourceRequest request
    ) {
        return service.saveSource(
                id, sourceId, request.sourceType(), request.name(), request.config(), request.enabled()
        );
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/sources/{sourceId}/sync")
    public List<Document> sync(@PathVariable String id, @PathVariable String sourceId) {
        return service.syncSource(id, sourceId);
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public Document importDocument(
            @PathVariable String id,
            @Valid @RequestBody DocumentRequest request
    ) {
        return service.importDocument(
                id, request.sourceId(), request.title(), request.path(), request.content(),
                request.language(), request.mimeType(), request.metadata(), request.permissions()
        );
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/process")
    public View process(@PathVariable String id) {
        return service.process(id);
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/publish")
    public View publish(@PathVariable String id) {
        return service.publish(id);
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/archive")
    public View archive(@PathVariable String id) {
        return service.archive(id);
    }

    @GetMapping("/api/v1/ai/admin/knowledge/{id}/versions")
    public List<Version> versions(@PathVariable String id) {
        return service.versions(id);
    }

    @PostMapping("/api/v1/ai/admin/knowledge/{id}/versions/{version}/rollback")
    public View rollback(@PathVariable String id, @PathVariable int version) {
        return service.rollback(id, version);
    }

    @PostMapping("/api/v1/ai/knowledge/{reference}/search")
    public SearchResult retrieve(
            @PathVariable String reference,
            @Valid @RequestBody SearchRequest request
    ) {
        return service.retrieve(reference, request.query(), request.topK());
    }

    private PolicySpec policy(PolicyRequest request) {
        return request == null ? null : new PolicySpec(
                request.topK(),
                request.strategy(),
                request.scoreThreshold(),
                request.mmrLambda(),
                request.metadataFilter(),
                request.timeWeight(),
                request.chunkStrategy(),
                request.chunkSize(),
                request.chunkOverlap()
        );
    }

    public record KnowledgeRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 32) String visibility,
            @Size(max = 100) String projectCode,
            @Size(max = 100) String departmentCode,
            @Size(max = 100) List<@NotBlank @Size(max = 240) String> permissions,
            @Valid PolicyRequest policy
    ) {
    }

    public record PolicyRequest(
            @Min(1) @Max(50) int topK,
            @Size(max = 32) String strategy,
            @DecimalMin("0") @DecimalMax("1") double scoreThreshold,
            @DecimalMin("0") @DecimalMax("1") double mmrLambda,
            Map<String, Object> metadataFilter,
            double timeWeight,
            @Size(max = 32) String chunkStrategy,
            @Min(32) @Max(8192) int chunkSize,
            @Min(0) int chunkOverlap
    ) {
    }

    public record SourceRequest(
            @NotBlank @Size(max = 40) String sourceType,
            @NotBlank @Size(max = 200) String name,
            Map<String, Object> config,
            boolean enabled
    ) {
    }

    public record DocumentRequest(
            @Size(max = 64) String sourceId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 2000) String path,
            @NotBlank @Size(max = 5000000) String content,
            @Size(max = 40) String language,
            @Size(max = 200) String mimeType,
            Map<String, Object> metadata,
            @Size(max = 100) List<@NotBlank @Size(max = 240) String> permissions
    ) {
    }

    public record SearchRequest(
            @NotBlank @Size(max = 100000) String query,
            @Min(1) @Max(50) Integer topK
    ) {
    }
}
