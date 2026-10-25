package com.dockmind.gateway;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The gateway is where a request's correlation ID is born. A valid incoming ID is kept,
 * anything else is replaced, and the ID is forwarded to the backend and returned to the caller.
 * Same rules as the services' servlet filter; this is the reactive (WebFlux) version.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        ServerWebExchange withId = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, id)))
                .build();
        withId.getResponse().getHeaders().set(HEADER, id);
        return chain.filter(withId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
