package org.simonegiusso.springweb.config.security;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** The problem-document vocabulary, shared by the filter chain and the controller advice. */
public enum Problems {
    ;

    public static final String BASE_URI = "https://api.spring-web.example/problems/";

    public static URI type(String name) {
        return URI.create(BASE_URI + name);
    }

    /**
     * Raised from two places — the filter chain for a denial it resolves itself, and the advice for
     * a {@code @PreAuthorize} denial, which is thrown inside Spring MVC and so never reaches the
     * {@code AccessDeniedHandler}. Both must answer identically.
     */
    public static ProblemDetail insufficientPermission() {
        return of(FORBIDDEN, type("insufficient-permission"), "Insufficient permission",
            "The token does not carry a role that permits this operation.");
    }

    public static ProblemDetail of(HttpStatus status, URI type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }
}
