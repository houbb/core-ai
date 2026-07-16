package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.KnowledgeModels.ExternalDocument;
import io.coreplatform.ai.application.domain.KnowledgeModels.Source;

import java.util.List;

public interface KnowledgeSourcePort {

    List<ExternalDocument> sync(Source source);
}
