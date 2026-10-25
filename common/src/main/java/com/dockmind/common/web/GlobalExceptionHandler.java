package com.dockmind.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON responses.
 *
 * <p>Rule: clients get a stable, documented shape and a correlation ID they can quote
 * to support. They never get stack traces, SQL, class names or parser messages, which
 * can leak internals. Full details go to the server log instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final URI VALIDATION_TYPE = URI.create("urn:dockmind:problem:validation");
    static final URI MALFORMED_BODY_TYPE = URI.create("urn:dockmind:problem:malformed-body");
    static final URI NOT_FOUND_TYPE = URI.create("urn:dockmind:problem:not-found");
    static final URI INTERNAL_TYPE = URI.create("urn:dockmind:problem:internal");

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = baseProblem(HttpStatus.BAD_REQUEST, VALIDATION_TYPE,
                "Validation failed", "One or more fields are invalid.", request);

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toErrorEntry)
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // The parser message can echo input or reveal class names, so log it rather than return it.
        log.debug("Malformed request body: {}", ex.getMessage());
        return baseProblem(HttpStatus.BAD_REQUEST, MALFORMED_BODY_TYPE,
                "Malformed request body", "The request body could not be read.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Not found", ex.getMessage(), request);
    }

    /** Errors the application raised deliberately: their message was written to be shown. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail body = ex.getBody();
        return baseProblem(ex.getStatusCode(), body.getType(), body.getTitle(), body.getDetail(), request);
    }

    /** A path or query parameter had the wrong type, e.g. "abc" where a UUID or date was expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.BAD_REQUEST, VALIDATION_TYPE, "Validation failed",
                "Parameter '" + ex.getName() + "' has an invalid value.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // Spring's own framework errors (unknown URL -> 404, wrong HTTP method -> 405,
        // unsupported content type -> 415, ...) implement ErrorResponse and already know
        // their correct status. Keep that status instead of turning them into a 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            String reason = known != null ? known.getReasonPhrase() : "Request failed";
            return baseProblem(status, URI.create("urn:dockmind:problem:http-" + status.value()),
                    reason, reason + ".", request);
        }
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return baseProblem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_TYPE,
                "Internal error", "An unexpected error occurred.", request);
    }

    /**
     * Builds the standard DockMind problem response. Public so service-specific advice
     * (for example mapping database conflicts) produces exactly the same shape.
     */
    public static ProblemDetail baseProblem(HttpStatusCode status, URI type, String title,
                                            String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    private static Map<String, String> toErrorEntry(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"));
    }
}
