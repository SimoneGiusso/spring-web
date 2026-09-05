package org.simonegiusso.springweb.config.security;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.simonegiusso.springweb.config.security.Permission.READ_ALL;

import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * The caller of the current request, taken from the validated token: {@code oid} identifies the
 * service principal and becomes the tenant, {@code roles} become its permissions.
 */
@Component
@RequestScope
public class CurrentUser {

    private final String objectId;
    private final Set<Permission> permissions;

    CurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        validateIfAuthenticated(authentication);
        objectId = authentication.getName();
        permissions = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(Permission::ofRole)
            .flatMap(Optional::stream)
            .collect(toUnmodifiableSet());
    }

    private static void validateIfAuthenticated(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated caller");
        }
    }

    public String objectId() {
        return objectId;
    }

    public boolean canReadEveryOwner() {
        return permissions.contains(READ_ALL);
    }
}
