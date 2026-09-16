package org.simonegiusso.springweb.utils;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.server.ResponseStatusException;

public final class HTTPResponse {

    public static ResponseStatusException invalid(String detail) {
        return new ResponseStatusException(BAD_REQUEST, detail);
    }

}
