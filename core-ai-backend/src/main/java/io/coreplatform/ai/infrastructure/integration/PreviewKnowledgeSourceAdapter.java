package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.KnowledgeModels.ExternalDocument;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import io.coreplatform.ai.application.port.KnowledgeSourcePort;

import java.util.List;
import java.util.Map;

public class PreviewKnowledgeSourceAdapter implements KnowledgeSourcePort {

    @Override
    public List<ExternalDocument> sync(Source source) {
        Object inline = source.config().get("content");
        if (inline instanceof String content && !content.isBlank()) {
            return List.of(new ExternalDocument(
                    source.name(),
                    "inline://" + source.id(),
                    content,
                    "auto",
                    "text/plain",
                    Map.of("mode", "LOCAL_INLINE")
            ));
        }
        return List.of();
    }
}
