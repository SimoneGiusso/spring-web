package org.simonegiusso.springweb.config.security;

/**
 * Entra app roles, as they appear in the {@code roles} claim.
 */
public enum Roles {
    ;

    public static final String READ = "Catalog.Read";
    public static final String READ_WRITE = "Catalog.ReadWrite";
    public static final String READ_ALL = "Catalog.Read.All";

}
