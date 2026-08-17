package org.simonegiusso.springweb.web;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.simonegiusso.springweb.product.DuplicateSkuException;
import org.simonegiusso.springweb.product.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_BASE_URI = "https://api.spring-web.example/problems/";

    private static final URI PRODUCT_NOT_FOUND = problemType("product-not-found");
    private static final URI DUPLICATE_SKU = problemType("duplicate-sku");
    private static final URI RESOURCE_CONFLICT = problemType("resource-conflict");
    private static final URI VALIDATION_FAILED = problemType("validation-failed");
    private static final URI INTERNAL_ERROR = problemType("internal-error");

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleProductNotFound(ProductNotFoundException exception) {
        ProblemDetail problem =
            problem(NOT_FOUND, PRODUCT_NOT_FOUND, "Product not found", exception.getMessage());
        problem.setProperty("productId", exception.getProductId());
        return problem;
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ProblemDetail handleDuplicateSku(DuplicateSkuException exception) {
        ProblemDetail problem =
            problem(CONFLICT, DUPLICATE_SKU, "Duplicate SKU", exception.getMessage());
        problem.setProperty("sku", exception.getSku());
        return problem;
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

    record ValidationError(String field, String message) {}
}
