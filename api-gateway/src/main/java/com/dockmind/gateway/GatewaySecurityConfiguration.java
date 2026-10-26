package com.dockmind.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Edge security: every {@code /api/**} request must carry a valid token, checked here before
 * any backend is contacted, so forged or expired tokens never consume backend resources.
 * Fine-grained role rules stay in each service, which validates the token again.
 */
@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        ServerAuthenticationEntryPoint problemJson = (exchange, ex) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().set("WWW-Authenticate", "Bearer");
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
            String body = "{\"type\":\"urn:dockmind:problem:unauthorized\",\"title\":\"Unauthorized\",\"status\":401,"
                    + "\"detail\":\"A valid access token is required.\""
                    + (correlationId == null ? "" : ",\"correlationId\":\"" + correlationId + "\"") + "}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }).authenticationEntryPoint(problemJson))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemJson))
                .build();
    }
}
