package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.port.PromptPermissionPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SecurityPromptPermissionAdapter implements PromptPermissionPort {

    @Override
    public boolean canRead(PromptData prompt) {
        Authentication authentication = authentication();
        if (isLocal(authentication) || hasScope(authentication, "SCOPE_AI.PROMPT.MANAGE")) {
            return true;
        }
        if (prompt.visibility() == null || prompt.visibility().name().equals("PUBLIC")) {
            return true;
        }
        if (prompt.ownerUser().equalsIgnoreCase(authentication.getName())) {
            return true;
        }
        Claims claims = claims(authentication);
        return switch (prompt.visibility()) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case PROJECT -> claims.projects().contains(normalize(prompt.projectCode()));
            case DEPARTMENT -> claims.departments().contains(normalize(prompt.departmentCode()));
        };
    }

    @Override
    public boolean canManage(PromptData prompt) {
        Authentication authentication = authentication();
        return isLocal(authentication)
                || hasScope(authentication, "SCOPE_AI.PROMPT.MANAGE")
                || prompt.ownerUser().equalsIgnoreCase(authentication.getName());
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isLocal(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName());
    }

    private boolean hasScope(Authentication authentication, String scope) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalize)
                .anyMatch(scope::equals);
    }

    private Claims claims(Authentication authentication) {
        Set<String> projects = new HashSet<>();
        Set<String> departments = new HashSet<>();
        if (authentication instanceof JwtAuthenticationToken token) {
            addClaimValues(token.getToken().getClaims().get("projects"), projects);
            addClaimValues(token.getToken().getClaims().get("project"), projects);
            addClaimValues(token.getToken().getClaims().get("project_id"), projects);
            addClaimValues(token.getToken().getClaims().get("department"), departments);
            addClaimValues(token.getToken().getClaims().get("department_id"), departments);
        }
        return new Claims(Set.copyOf(projects), Set.copyOf(departments));
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

    private record Claims(Set<String> projects, Set<String> departments) {
    }
}
