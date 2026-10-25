package com.dockmind.shipment.api;

import com.dockmind.common.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Database conflicts become 409s instead of 500s. Runs before the shared handler.
 *
 * <ul>
 *   <li><b>Optimistic lock failure:</b> someone else changed the record since you read it.
 *       The client should reload and retry.</li>
 *   <li><b>Integrity violation:</b> usually two requests racing to book the same reference;
 *       the database's UNIQUE constraint caught what the earlier check couldn't.</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);
    private static final URI CONFLICT = URI.create("urn:dockmind:problem:conflict");

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return GlobalExceptionHandler.baseProblem(HttpStatus.CONFLICT, CONFLICT, "Conflict",
                "The shipment was changed by someone else. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.info("Integrity violation for {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return GlobalExceptionHandler.baseProblem(HttpStatus.CONFLICT, CONFLICT, "Conflict",
                "The request conflicts with existing data.", request);
    }
}
