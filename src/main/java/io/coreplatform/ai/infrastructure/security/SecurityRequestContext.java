package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.application.port.RequestContextPort;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityRequestContext implements RequestContextPort {

    @Override
    public String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "local-admin";
        }
        return authentication.getName();
    }

    @Override
    public String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "system" : traceId;
    }
}
