package org.simonegiusso.springweb.config.persistence;

public class UnidentifiedTenantException extends RuntimeException {

    UnidentifiedTenantException(String message) {
        super(message);
    }
}
