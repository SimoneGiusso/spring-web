package org.simonegiusso.springweb.config.persistence;

public class InsufficientPermissionException extends RuntimeException {

    InsufficientPermissionException(String message) {
        super(message);
    }
}
