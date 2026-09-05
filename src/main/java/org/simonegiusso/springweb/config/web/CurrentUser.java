package org.simonegiusso.springweb.config.web;

import static org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor.USER_HEADER;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CurrentUser {

    private final String name;

    CurrentUser(HttpServletRequest request) {
        name = request.getHeader(USER_HEADER);
    }

    public String name() {
        return name;
    }
}
