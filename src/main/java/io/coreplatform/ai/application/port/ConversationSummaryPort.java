package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ConversationModels.Message;

import java.util.List;

public interface ConversationSummaryPort {

    String summarize(List<Message> messages);

    String mode();
}
