package com.dockmind.dock.api;

import com.dockmind.common.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Database conflicts become 409s, as in the shipment service. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {

    private static final URI CONFLICT = URI.create("urn:dockmind:problem:conflict");

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflict(Exception ex, HttpServletRequest request) {
        return GlobalExceptionHandler.baseProblem(HttpStatus.CONFLICT, CONFLICT, "Conflict",
                "The request conflicts with existing data. Reload and try again.", request);
    }
}
