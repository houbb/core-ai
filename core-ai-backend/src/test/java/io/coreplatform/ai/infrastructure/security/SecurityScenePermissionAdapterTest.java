package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityScenePermissionAdapterTest {

    private final SecurityScenePermissionAdapter adapter = new SecurityScenePermissionAdapter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldMatchRoleFromJwtClaimsWithoutCallingIdentity() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("developer")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of("DEVELOPER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_ai.scene.execute"))
        ));

        assertThat(adapter.hasAccess(List.of(permission(ScenePermissionType.ROLE, "DEVELOPER"))))
                .isTrue();
        assertThat(adapter.hasAccess(List.of(permission(ScenePermissionType.DEPARTMENT, "FINANCE"))))
                .isFalse();
    }

    @Test
    void shouldAllowLocalModeWithoutExternalIdentity() {
        assertThat(adapter.hasAccess(List.of(permission(ScenePermissionType.ROLE, "ADMIN"))))
                .isTrue();
    }

    private ScenePermission permission(ScenePermissionType type, String value) {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new ScenePermission(
                "permission",
                "scene",
                type,
                value,
                now,
                now,
                "test",
                "test"
        );
    }
}
