package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AnalyticsModels.UsageEvent;

public interface AnalyticsEventPort {

    void record(UsageEvent event);
}
