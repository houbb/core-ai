package io.coreplatform.ai.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDiffServiceTest {

    @Test
    void shouldProduceGitLikeAddedAndRemovedLines() {
        var result = new PromptDiffService().compare(
                "USER",
                "Translate\n{{content}}",
                "Translate professionally\n{{content}}"
        );

        assertThat(result)
                .extracting("type")
                .contains("REMOVED", "ADDED", "SAME");
        assertThat(result)
                .filteredOn(line -> line.type().equals("ADDED"))
                .extracting("text")
                .containsExactly("Translate professionally");
    }
}
