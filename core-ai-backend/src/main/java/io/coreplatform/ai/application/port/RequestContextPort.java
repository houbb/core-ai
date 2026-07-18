package io.coreplatform.ai.application.port;

public interface RequestContextPort {

    String actor();

    String traceId();
}
