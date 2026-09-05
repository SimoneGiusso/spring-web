package org.simonegiusso.springweb.config.security;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the two failures Spring Security raises inside the filter chain, before Spring MVC and so
 * out of reach of the {@code @RestControllerAdvice}, as the same problem documents as every other
 * error.
 */
@Component
class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final URI UNAUTHENTICATED = Problems.type("unauthenticated");

    private final ObjectMapper json;

    ProblemSecurityHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
        throws IOException {
        write(request, response, Problems.of(UNAUTHORIZED, UNAUTHENTICATED, "Unauthenticated",
            "A valid bearer token is required."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
        throws IOException {
        write(request, response, Problems.insufficientPermission());
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem) throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
