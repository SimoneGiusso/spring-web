package org.simonegiusso.springweb.config.persistence;

import static org.springframework.util.StringUtils.hasText;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Binds the caller named by {@value #USER_HEADER} to the request thread, so that every Hibernate
 * session opened while the request is handled is scoped to that tenant. It stands in for
 * authentication until authentication exists.
 */
public class TenantHeaderInterceptor implements HandlerInterceptor {

    public static final String USER_HEADER = "X-User";

    private static final int MAX_LENGTH = 64;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String user = request.getHeader(USER_HEADER);
        if (!hasText(user) || user.length() > MAX_LENGTH) {
            throw new UnidentifiedTenantException(
                "Header " + USER_HEADER + " must carry a non-blank user id of at most " + MAX_LENGTH + " characters");
        }
        if (HibernateTenantConfiguration.SYSTEM.equals(user)) {
            throw new UnidentifiedTenantException("Header " + USER_HEADER + " must not claim the reserved system identity");
        }
        return true;
    }
}
