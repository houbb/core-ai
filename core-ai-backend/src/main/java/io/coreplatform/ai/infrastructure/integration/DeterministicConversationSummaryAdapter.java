package io.coreplatform.ai.infrastructure.integration;

import io.coreplatform.ai.application.domain.ConversationModels.Message;
import io.coreplatform.ai.application.port.ConversationSummaryPort;

import java.util.List;
import java.util.stream.Collectors;

public class DeterministicConversationSummaryAdapter implements ConversationSummaryPort {

    @Override
    public String summarize(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "No conversation history.";
        }
        return messages.stream()
                .map(message -> message.role() + ": " + compact(message.content()))
                .collect(Collectors.joining(" | "));
    }

    @Override
    public String mode() {
        return "DETERMINISTIC";
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }
}
