package com.dockmind.shipment.security;

import com.dockmind.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 401 and 403 responses in the same RFC 9457 shape as every other DockMind error.
 *
 * <p>These run inside the security filter chain, before Spring MVC, so the normal
 * {@code @RestControllerAdvice} can't produce them. The body is deliberately generic: it
 * never says <i>why</i> a token was rejected (expired, bad signature, wrong issuer), which
 * would help an attacker. The reason is logged by Spring Security at debug level.
 */
final class ProblemJsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, request, 401, "unauthorized", "Unauthorized",
                "A valid access token is required.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, 403, "forbidden", "Forbidden",
                "Your role does not allow this action.");
    }

    private static void write(HttpServletResponse response, HttpServletRequest request, int status,
                              String code, String title, String detail) throws IOException {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        StringBuilder json = new StringBuilder(256)
                .append("{\"type\":\"urn:dockmind:problem:").append(code).append('"')
                .append(",\"title\":\"").append(title).append('"')
                .append(",\"status\":").append(status)
                .append(",\"detail\":\"").append(detail).append('"')
                .append(",\"instance\":\"").append(escape(request.getRequestURI())).append('"');
        if (correlationId != null) {
            json.append(",\"correlationId\":\"").append(escape(correlationId)).append('"');
        }
        json.append('}');
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
    }

    /** Minimal JSON string escaping for values that come from the request. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
