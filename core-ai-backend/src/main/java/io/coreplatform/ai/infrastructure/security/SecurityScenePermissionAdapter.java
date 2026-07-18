package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.port.ScenePermissionPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SecurityScenePermissionAdapter implements ScenePermissionPort {

    @Override
    public boolean hasAccess(List<ScenePermission> permissions) {
        if (permissions == null || permissions.isEmpty()
                || permissions.stream().anyMatch(item -> item.type() == ScenePermissionType.EVERYONE)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return true;
        }
        Set<String> authorities = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            authorities.add(authority.getAuthority().toUpperCase(Locale.ROOT));
        }
        if (authorities.contains("SCOPE_AI.SCENE.MANAGE")) {
            return true;
        }

        Set<String> roles = new HashSet<>();
        Set<String> departments = new HashSet<>();
        Set<String> groups = new HashSet<>();
        if (authentication instanceof JwtAuthenticationToken token) {
            addClaimValues(token.getToken().getClaims().get("roles"), roles);
            addClaimValues(token.getToken().getClaims().get("role"), roles);
            addClaimValues(token.getToken().getClaims().get("department"), departments);
            addClaimValues(token.getToken().getClaims().get("department_id"), departments);
            addClaimValues(token.getToken().getClaims().get("groups"), groups);
            addClaimValues(token.getToken().getClaims().get("user_groups"), groups);
        }
        authorities.stream()
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring(5))
                .forEach(roles::add);

        return permissions.stream().anyMatch(permission -> switch (permission.type()) {
            case EVERYONE -> true;
            case ROLE -> roles.contains(normalize(permission.value()));
            case DEPARTMENT -> departments.contains(normalize(permission.value()));
            case USER_GROUP -> groups.contains(normalize(permission.value()));
        });
    }

    private void addClaimValues(Object claim, Set<String> values) {
        if (claim instanceof Collection<?> collection) {
            collection.forEach(item -> values.add(normalize(String.valueOf(item))));
        } else if (claim != null) {
            values.add(normalize(String.valueOf(claim)));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
