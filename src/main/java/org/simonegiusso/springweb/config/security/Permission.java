package org.simonegiusso.springweb.config.security;

import java.util.Arrays;
import java.util.Optional;

public enum Permission {

    READ(Roles.READ),
    READ_WRITE(Roles.READ_WRITE),
    READ_ALL(Roles.READ_ALL);

    private final String role;

    Permission(String role) {
        this.role = role;
    }

    static Optional<Permission> ofRole(String role) {
        return Arrays.stream(values()).filter(permission -> permission.role.equals(role)).findFirst();
    }

    public String role() {
        return role;
    }
}
