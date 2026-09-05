package org.simonegiusso.springweb.config.persistence;

import static org.simonegiusso.springweb.config.web.Permission.READ_WRITE;
import static org.springframework.util.StringUtils.hasText;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.simonegiusso.springweb.config.web.Permission;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Checks who the caller claims to be and what it may do, before any handler runs.
 */
public class TenantHeaderInterceptor implements HandlerInterceptor {

    public static final String USER_HEADER = "X-User";
    public static final String PERMISSION_HEADER = "X-Permission";

    private static final int MAX_LENGTH = 64;
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        validateUser(request);
        validatePermission(request);
        return true;
    }

    private static void validateUser(HttpServletRequest request) {
        String user = request.getHeader(USER_HEADER);

        if (!hasText(user) || user.length() > MAX_LENGTH) {
            throw new UnidentifiedTenantException(
                "Header " + USER_HEADER + " must carry a non-blank user id of at most " + MAX_LENGTH + " characters");
        }

        if (HibernateTenantConfiguration.SYSTEM.equals(user)) {
            throw new UnidentifiedTenantException("Header " + USER_HEADER + " must not claim the reserved system identity");
        }
    }

    private static void validatePermission(HttpServletRequest request) {
        Permission permission = Permission.parse(request.getHeader(PERMISSION_HEADER))
            .orElseThrow(() -> new InsufficientPermissionException(
                "No permission claimed; header " + PERMISSION_HEADER + " must grant one of " + Permission.names()));

        if (isWriteHttpMethod(request) && !permission.canWrite()) {
            throw new InsufficientPermissionException(
                "Permission " + permission + " may not modify products; " + READ_WRITE + " is required");
        }
    }

    private static boolean isWriteHttpMethod(HttpServletRequest request) {
        return !READ_ONLY_METHODS.contains(request.getMethod());
    }
}
