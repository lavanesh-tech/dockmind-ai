package com.dockmind.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/shipments");

    @BeforeEach
    void setCorrelationId() {
        MDC.put(CorrelationId.MDC_KEY, "test-correlation-id");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validationErrorsListEachInvalidField() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "shipmentRequest");
        bindingResult.addError(new FieldError("shipmentRequest", "carrierCode", "must not be blank"));
        bindingResult.addError(new FieldError("shipmentRequest", "palletCount", "must be greater than 0"));
        MethodParameter parameter = new MethodParameter(
                SampleController.class.getDeclaredMethod("create", Object.class), 0);

        ProblemDetail problem = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).isEqualTo(GlobalExceptionHandler.VALIDATION_TYPE);
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/shipments"));
        assertThat(problem.getProperties())
                .containsEntry("correlationId", "test-correlation-id")
                .containsEntry("errors", List.of(
                        Map.of("field", "carrierCode", "message", "must not be blank"),
                        Map.of("field", "palletCount", "message", "must be greater than 0")));
    }

    @Test
    void malformedBodyDoesNotEchoParserMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character at com.fasterxml.Internal", new MockHttpInputMessage(new byte[0]));

        ProblemDetail problem = handler.handleMalformedBody(ex, request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("The request body could not be read.");
        assertThat(problem.getDetail()).doesNotContain("com.fasterxml");
    }

    @Test
    void notFoundReturns404WithSafeMessage() {
        ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException("Shipment", 42), request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Shipment 42 was not found");
    }

    @Test
    void unexpectedErrorsHideInternalDetails() {
        Exception ex = new RuntimeException(new SQLException("password authentication failed for user dockmind"));

        ProblemDetail problem = handler.handleUnexpected(ex, request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(problem.toString()).doesNotContain("password");
        assertThat(problem.getProperties()).containsEntry("correlationId", "test-correlation-id");
    }

    @Test
    void frameworkErrorsKeepTheirOwnStatus() {
        // ErrorResponseException is Spring's general ErrorResponse; NoResourceFoundException
        // (unknown URL) and HttpRequestMethodNotSupportedException behave the same way.
        Exception ex = new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED);

        ProblemDetail problem = handler.handleUnexpected(ex, request);

        assertThat(problem.getStatus()).isEqualTo(405);
        assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
    }

    /** Exists only so the test can build a real MethodParameter. */
    @SuppressWarnings("unused")
    private static final class SampleController {
        void create(Object body) {
        }
    }
}
