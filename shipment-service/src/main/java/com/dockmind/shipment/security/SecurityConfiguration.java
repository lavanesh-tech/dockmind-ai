package com.dockmind.shipment.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what.
 *
 * <ul>
 *   <li>Health, info and the API docs are public, so load balancers and reviewers can reach them.</li>
 *   <li>Reading shipments needs any DockMind role.</li>
 *   <li>Changing shipments needs DISPATCHER or ADMIN.</li>
 *   <li>Anything not listed is denied, so a new endpoint is closed until someone opens it on purpose.</li>
 * </ul>
 *
 * <p>The API is stateless: no session or cookie, just a bearer token on every request. That's
 * also why CSRF protection is off. CSRF attacks ride on cookies the browser sends
 * automatically, and a bearer token in an {@code Authorization} header is never sent automatically.
 *
 * <p>{@code @ConditionalOnWebApplication}: HTTP security only exists when there is an HTTP
 * server. Without it, a context with no web server (such as the repository test) can't start.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        ProblemJsonSecurityHandlers problems = new ProblemJsonSecurityHandlers();
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll() // Boot's fallback error page; never leaks internals (see application.yml)
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipments", "/api/v1/shipments/**")
                        .hasAnyRole(Roles.VIEWER, Roles.DISPATCHER, Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/shipments", "/api/v1/shipments/**")
                        .hasAnyRole(Roles.DISPATCHER, Roles.ADMIN)
                        .requestMatchers("/api/**").authenticated() // unknown API paths: 401/404, never open
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new DockMindJwtConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }
}
