package com.dockmind.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * An error the application raises on purpose, with a message that is safe to show the client.
 *
 * <p>Use the factory methods: {@code ApiException.conflict("...")}, {@code badRequest},
 * {@code forbidden}. Each produces a problem-detail response with a stable {@code type} URN
 * that clients can branch on, rather than parsing the human-readable message.
 *
 * <p>Never put another tenant's data, SQL or stack details in the message.
 */
public class ApiException extends ErrorResponseException {

    public ApiException(HttpStatus status, String code, String detail) {
        super(status);
        setType(URI.create("urn:dockmind:problem:" + code));
        setTitle(status.getReasonPhrase());
        setDetail(detail);
    }

    /** The request is valid but conflicts with current state (duplicate, illegal state change, concurrent edit). */
    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", detail);
    }

    /** The request is well-formed but its content breaks a business rule. */
    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad-request", detail);
    }

    /** The caller is authenticated but not allowed to do this. */
    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", detail);
    }
}
