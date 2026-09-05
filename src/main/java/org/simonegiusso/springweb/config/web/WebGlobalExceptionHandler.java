package org.simonegiusso.springweb.config.web;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.simonegiusso.springweb.config.persistence.UnidentifiedTenantException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
class WebGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_BASE_URI = "https://api.spring-web.example/problems/";

    private static final URI RESOURCE_NOT_FOUND = problemType("resource-not-found");
    private static final URI RESOURCE_CONFLICT = problemType("resource-conflict");
    private static final URI VALIDATION_FAILED = problemType("validation-failed");
    private static final URI UNIDENTIFIED_TENANT = problemType("unidentified-tenant");
    private static final URI INTERNAL_ERROR = problemType("internal-error");

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException exception) {
        return problem(NOT_FOUND, RESOURCE_NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(UnidentifiedTenantException.class)
    ProblemDetail handleUnidentifiedTenant(UnidentifiedTenantException exception) {
        return problem(BAD_REQUEST, UNIDENTIFIED_TENANT, "Unidentified tenant", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Database constraint violated", exception);
        return problem(
            CONFLICT,
            RESOURCE_CONFLICT,
            "Conflicting resource state",
            "The request conflicts with the current state of the resource.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(
            INTERNAL_SERVER_ERROR,
            INTERNAL_ERROR,
            "Internal server error",
            "The request could not be processed. Please retry later.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {

        ProblemDetail problem = exception.getBody();
        problem.setType(VALIDATION_FAILED);
        problem.setTitle("Validation failed");
        problem.setDetail("The request body failed validation. See the errors field for details.");
        problem.setProperty("errors", validationErrors(exception));
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static List<ValidationError> validationErrors(MethodArgumentNotValidException exception) {
        return Stream.concat(
                exception.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ValidationError(error.getField(), message(error))),
                exception.getBindingResult().getGlobalErrors().stream()
                    .map(error -> new ValidationError(error.getObjectName(), message(error))))
            .sorted(Comparator.comparing(ValidationError::field))
            .toList();
    }

    private static String message(ObjectError error) {
        return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }

    private static URI problemType(String name) {
        return URI.create(PROBLEM_BASE_URI + name);
    }

    private record ValidationError(String field, String message) {}
}
