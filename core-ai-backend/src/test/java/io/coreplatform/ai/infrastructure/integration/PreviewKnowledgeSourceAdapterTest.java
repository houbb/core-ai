package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.KnowledgeModels.ExternalDocument;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewKnowledgeSourceAdapterTest {

    @Test
    void shouldImportInlineContentAndLeaveExternalSourcesNonBlocking() {
        Instant now = Instant.now();
        PreviewKnowledgeSourceAdapter adapter = new PreviewKnowledgeSourceAdapter();
        Source inline = new Source(
                "source", "knowledge", "API", "Inline",
                Map.of("content", "core-ai local knowledge"), "PENDING", null,
                true, now, now, "tester", "tester"
        );

        List<ExternalDocument> documents = adapter.sync(inline);
        assertThat(documents).singleElement()
                .satisfies(document -> {
                    assertThat(document.content()).isEqualTo("core-ai local knowledge");
                    assertThat(document.metadata()).containsEntry("mode", "LOCAL_INLINE");
                });

        Source external = new Source(
                "external", "knowledge", "WEBSITE", "External",
                Map.of("url", "https://example.invalid"), "PENDING", null,
                true, now, now, "tester", "tester"
        );
        assertThat(adapter.sync(external)).isEmpty();
    }
}
