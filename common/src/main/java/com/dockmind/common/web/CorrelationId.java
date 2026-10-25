package com.dockmind.common.web;

import java.util.regex.Pattern;

/**
 * Names and rules for the correlation ID that ties together every log line and
 * downstream call made while handling one request.
 */
public final class CorrelationId {

    /** HTTP header read from incoming requests and echoed on every response. */
    public static final String HEADER = "X-Correlation-Id";

    /** Key under which the ID is stored in the SLF4J MDC, so log patterns can print it. */
    public static final String MDC_KEY = "correlationId";

    /** Request attribute holding the ID, for code that has the request but not the MDC. */
    public static final String REQUEST_ATTRIBUTE = CorrelationId.class.getName();

    /**
     * Accepted shape for a client-supplied ID. Anything else is replaced, which stops
     * log injection (for example an ID containing a newline that forges a log line)
     * and stops clients from filling logs with very long values.
     */
    static final Pattern VALID_FORMAT = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private CorrelationId() {
    }

    /** Returns true if the value may be reused as a correlation ID. */
    public static boolean isValid(String value) {
        return value != null && VALID_FORMAT.matcher(value).matches();
    }
}
