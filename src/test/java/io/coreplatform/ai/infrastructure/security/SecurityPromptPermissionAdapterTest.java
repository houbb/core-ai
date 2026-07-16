package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptVisibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPromptPermissionAdapterTest {

    private final SecurityPromptPermissionAdapter adapter = new SecurityPromptPermissionAdapter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRespectDepartmentAndPrivateOwnership() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "alice", "department", "finance")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_ai.prompt.read")),
                "alice"
        ));

        assertThat(adapter.canRead(prompt(PromptVisibility.DEPARTMENT, "bob", "FINANCE"))).isTrue();
        assertThat(adapter.canRead(prompt(PromptVisibility.PRIVATE, "bob", null))).isFalse();
        assertThat(adapter.canRead(prompt(PromptVisibility.PRIVATE, "alice", null))).isTrue();
    }

    @Test
    void shouldAllowLocalModeWithoutExternalIdentity() {
        assertThat(adapter.canRead(prompt(PromptVisibility.PRIVATE, "owner", null))).isTrue();
        assertThat(adapter.canManage(prompt(PromptVisibility.PRIVATE, "owner", null))).isTrue();
    }

    private PromptData prompt(
            PromptVisibility visibility,
            String owner,
            String department
    ) {
        Instant now = Instant.now();
        return new PromptData(
                "id",
                "prompt",
                "Prompt",
                null,
                "GENERAL",
                null,
                PromptStatus.DRAFT,
                1,
                null,
                visibility,
                null,
                department,
                owner,
                now,
                now,
                owner,
                owner
        );
    }
}
