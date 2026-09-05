package org.simonegiusso.springweb.config.web;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Optional;

public enum Permission {

    /** Read the caller's own products. */
    READ,
    /** Read and modify the caller's own products. */
    READ_WRITE,
    /** Read every owner's products, and modify none. */
    READ_ALL;

    public static String names() {
        return Arrays.stream(values()).map(Permission::name).collect(joining(", "));
    }

    public static Optional<Permission> parse(String value) {
        return Arrays.stream(values())
            .filter(permission -> permission.name().equalsIgnoreCase(value))
            .findFirst();
    }

    public boolean canWrite() {
        return this == READ_WRITE;
    }

    public boolean canReadEveryOwner() {
        return this == READ_ALL;
    }
}
