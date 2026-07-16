package io.coreplatform.ai.api.controller;

import io.coreplatform.ai.application.domain.ConversationModels.ChatResult;
import io.coreplatform.ai.application.domain.ConversationModels.Conversation;
import io.coreplatform.ai.application.domain.ConversationModels.ExportPackage;
import io.coreplatform.ai.application.domain.ConversationModels.Memory;
import io.coreplatform.ai.application.domain.ConversationModels.Message;
import io.coreplatform.ai.application.domain.ConversationModels.Session;
import io.coreplatform.ai.application.domain.ConversationModels.Share;
import io.coreplatform.ai.application.domain.ConversationModels.Summary;
import io.coreplatform.ai.application.domain.ConversationModels.View;
import io.coreplatform.ai.application.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/ai/conversations")
    public List<Conversation> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    @PostMapping("/api/v1/ai/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody ConversationRequest request) {
        return service.create(request.title(), request.sceneCode(), request.tags());
    }

    @GetMapping("/api/v1/ai/conversations/{id}")
    public View get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/ai/conversations/{id}")
    public View update(@PathVariable String id, @Valid @RequestBody UpdateConversationRequest request) {
        return service.update(
                id,
                request.title(),
                request.sceneCode(),
                request.favorite(),
                request.status(),
                request.tags()
        );
    }

    @DeleteMapping("/api/v1/ai/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/api/v1/ai/conversations/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Session newSession(
            @PathVariable String id,
            @RequestBody(required = false) SessionRequest request
    ) {
        return service.newSession(id, request == null ? null : request.name());
    }

    @PostMapping("/api/v1/ai/conversations/{id}/messages")
    public ChatResult send(
            @PathVariable String id,
            @Valid @RequestBody MessageRequest request
    ) {
        return service.send(
                id,
                request.sessionId(),
                request.content(),
                request.contentType(),
                request.parameters()
        );
    }

    @PutMapping("/api/v1/ai/messages/{id}")
    public Message editMessage(@PathVariable String id, @Valid @RequestBody EditMessageRequest request) {
        return service.editMessage(id, request.content());
    }

    @PostMapping("/api/v1/ai/conversations/{id}/summary")
    public Summary summarize(@PathVariable String id) {
        return service.summarize(id);
    }

    @PostMapping("/api/v1/ai/conversations/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public Share share(
            @PathVariable String id,
            @RequestBody(required = false) ShareRequest request
    ) {
        return service.share(id, request == null ? 24 : request.expiresInHours());
    }

    @DeleteMapping("/api/v1/ai/conversations/{id}/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@PathVariable String id, @PathVariable String shareId) {
        service.revokeShare(id, shareId);
    }

    @GetMapping("/api/v1/ai/conversation-shares/{code}")
    public View shared(@PathVariable String code) {
        return service.shared(code);
    }

    @GetMapping("/api/v1/ai/conversations/{id}/export")
    public ExportPackage export(@PathVariable String id) {
        return service.export(id);
    }

    @PostMapping("/api/v1/ai/conversations/{id}/replay")
    public Map<String, Object> replay(
            @PathVariable String id,
            @RequestBody(required = false) ReplayRequest request
    ) {
        return service.replay(id, request == null ? null : request.sourceMessageId());
    }

    @GetMapping("/api/v1/ai/memories")
    public List<Memory> memories(
            @RequestParam String ownerType,
            @RequestParam String ownerId
    ) {
        return service.memories(ownerType, ownerId);
    }

    @PostMapping("/api/v1/ai/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public Memory createMemory(@Valid @RequestBody MemoryRequest request) {
        return saveMemory(null, request);
    }

    @PutMapping("/api/v1/ai/memories/{id}")
    public Memory updateMemory(@PathVariable String id, @Valid @RequestBody MemoryRequest request) {
        return saveMemory(id, request);
    }

    @PatchMapping("/api/v1/ai/memories/{id}/frozen")
    public Memory freeze(@PathVariable String id, @RequestBody FreezeRequest request) {
        return service.freezeMemory(id, request.frozen());
    }

    @DeleteMapping("/api/v1/ai/memories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(@PathVariable String id) {
        service.deleteMemory(id);
    }

    private Memory saveMemory(String id, MemoryRequest request) {
        return service.saveMemory(
                id,
                request.ownerType(),
                request.ownerId(),
                request.memoryType(),
                request.content(),
                request.importance(),
                request.source(),
                request.metadata()
        );
    }

    public record ConversationRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 100) String sceneCode,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> tags
    ) {
    }

    public record UpdateConversationRequest(
            @Size(max = 300) String title,
            @Size(max = 100) String sceneCode,
            Boolean favorite,
            @Size(max = 32) String status,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> tags
    ) {
    }

    public record SessionRequest(@Size(max = 200) String name) {
    }

    public record MessageRequest(
            @Size(max = 64) String sessionId,
            @NotBlank @Size(max = 1000000) String content,
            @Size(max = 32) String contentType,
            Map<String, Object> parameters
    ) {
    }

    public record EditMessageRequest(@NotBlank @Size(max = 1000000) String content) {
    }

    public record ShareRequest(@Min(0) @Max(8760) int expiresInHours) {
    }

    public record ReplayRequest(@Size(max = 64) String sourceMessageId) {
    }

    public record MemoryRequest(
            @NotBlank @Size(max = 32) String ownerType,
            @NotBlank @Size(max = 100) String ownerId,
            @NotBlank @Size(max = 32) String memoryType,
            @NotBlank @Size(max = 1000000) String content,
            @DecimalMin("0") @DecimalMax("1") double importance,
            @Size(max = 100) String source,
            Map<String, Object> metadata
    ) {
    }

    public record FreezeRequest(boolean frozen) {
    }
}
