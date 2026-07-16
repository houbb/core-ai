package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.ConversationModels.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicConversationSummaryAdapterTest {

    @Test
    void shouldSummarizeMessagesInStableOrderAndBoundLongContent() {
        Instant now = Instant.now();
        List<Message> messages = List.of(
                message("USER", "Hello runtime", 1, now),
                message("ASSISTANT", "x".repeat(300), 2, now)
        );

        DeterministicConversationSummaryAdapter adapter =
                new DeterministicConversationSummaryAdapter();
        String summary = adapter.summarize(messages);

        assertThat(adapter.mode()).isEqualTo("DETERMINISTIC");
        assertThat(summary).startsWith("USER: Hello runtime | ASSISTANT:");
        assertThat(summary).endsWith("...");
        assertThat(summary.length()).isLessThan(220);
    }

    private Message message(String role, String content, int sequence, Instant now) {
        return new Message(
                role + sequence, "session", role, content, "TEXT", content.length() / 4,
                sequence, 1, null, null, Map.of(), now, now, "tester", "tester"
        );
    }
}
