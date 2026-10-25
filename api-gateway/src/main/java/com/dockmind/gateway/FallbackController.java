package com.dockmind.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Set;

/**
 * What callers get when a backend is down, too slow, or its circuit is open: a fast, clear
 * 503 in the standard problem format, with the correlation ID to quote to support.
 */
@RestController
public class FallbackController {

    private static final Set<String> KNOWN = Set.of(RoutesConfiguration.SHIPMENT, RoutesConfiguration.DOCK,
            RoutesConfiguration.RECOVERY, RoutesConfiguration.AI);

    @RequestMapping("/fallback/{service}")
    public ProblemDetail unavailable(@PathVariable String service, ServerWebExchange exchange) {
        String name = KNOWN.contains(service) ? service : "service";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The " + name + " is temporarily unavailable. Please retry shortly.");
        problem.setType(URI.create("urn:dockmind:problem:service-unavailable"));
        problem.setTitle("Service unavailable");
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
