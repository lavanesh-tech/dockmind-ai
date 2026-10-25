package com.dockmind.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gives every request a correlation ID.
 *
 * <p>If the caller sends a valid {@value CorrelationId#HEADER} header (for example the
 * API gateway, or another service), that ID is reused so one user action can be traced
 * across services. Otherwise a new UUID is generated. The ID is placed in the MDC for
 * logging, stored as a request attribute and returned in the response header.
 *
 * <p>The MDC is backed by a {@link ThreadLocal}. Servlet containers reuse threads, so
 * the value must be removed in {@code finally}; otherwise the next request handled on
 * the same thread would log with a stale ID.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    private final Supplier<String> idGenerator;

    public CorrelationIdFilter() {
        this(() -> UUID.randomUUID().toString());
    }

    /** Constructor used by tests to make generated IDs predictable. */
    CorrelationIdFilter(Supplier<String> idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(CorrelationId.HEADER));

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        request.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    String resolve(String incoming) {
        return CorrelationId.isValid(incoming) ? incoming : idGenerator.get();
    }

    /** Runs before every other filter so even early failures are logged with an ID. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
