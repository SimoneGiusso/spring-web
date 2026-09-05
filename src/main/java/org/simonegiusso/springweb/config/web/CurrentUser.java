package org.simonegiusso.springweb.config.web;

import static org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor.PERMISSION_HEADER;
import static org.simonegiusso.springweb.config.persistence.TenantHeaderInterceptor.USER_HEADER;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CurrentUser {

    private final String name;
    private final Permission permission;

    CurrentUser(HttpServletRequest request) {
        name = request.getHeader(USER_HEADER);
        permission = Permission.parse(request.getHeader(PERMISSION_HEADER))
            .orElseThrow(() -> new IllegalStateException(PERMISSION_HEADER + " is not set"));
    }

    public String name() {
        return name;
    }

    public Permission permission() {
        return permission;
    }
}
