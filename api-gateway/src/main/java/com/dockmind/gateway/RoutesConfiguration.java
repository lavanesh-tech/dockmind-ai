package com.dockmind.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Which paths go to which service, and the protections on every route:
 * <ol>
 *   <li><b>Rate limit per tenant</b> (Redis token bucket): one noisy tenant can't starve the others.
 *       Over the limit gets 429 Too Many Requests.</li>
 *   <li><b>Circuit breaker + timeout</b> (Resilience4j): a slow or failing service gets a fast
 *       503 problem response instead of piling up waiting requests. After repeated failures the
 *       circuit opens and calls fail immediately until the service recovers.</li>
 * </ol>
 * The caller's {@code Authorization} header is passed through unchanged; each service validates
 * the token again (defence in depth) and applies its own role rules.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class RoutesConfiguration {

    public static final String SHIPMENT = "shipment-service";
    public static final String DOCK = "dock-scheduling-service";

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties props, RedisRateLimiter limiter,
                        KeyResolver tenantKeyResolver) {
        return builder.routes()
                .route(SHIPMENT, r -> r.path("/api/v1/shipments", "/api/v1/shipments/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(tenantKeyResolver))
                                .circuitBreaker(c -> c.setName(SHIPMENT).setFallbackUri("forward:/fallback/" + SHIPMENT)))
                        .uri(props.shipmentUri()))
                .route(DOCK, r -> r.path("/api/v1/docks", "/api/v1/docks/**", "/api/v1/schedules", "/api/v1/schedules/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> c.setRateLimiter(limiter).setKeyResolver(tenantKeyResolver))
                                .circuitBreaker(c -> c.setName(DOCK).setFallbackUri("forward:/fallback/" + DOCK)))
                        .uri(props.dockUri()))
                .build();
    }

    @Bean
    RedisRateLimiter redisRateLimiter(GatewayProperties props) {
        return new RedisRateLimiter(props.rateLimit().replenishRate(), props.rateLimit().burstCapacity(), 1);
    }

    /**
     * Rate-limit bucket = the tenant from the verified token, so limits apply per company.
     * Falls back to the user if a token has no tenant; the service will reject that request anyway.
     */
    @Bean
    KeyResolver tenantKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof JwtAuthenticationToken jwt) {
                        String tenant = jwt.getToken().getClaimAsString("tenant_id");
                        return Mono.just(tenant != null ? "tenant:" + tenant : "user:" + jwt.getName());
                    }
                    return Mono.just("user:" + principal.getName());
                })
                .defaultIfEmpty("anonymous");
    }
}
