package com.dockmind.recovery.security;

import com.dockmind.common.security.DockMindJwtConverter;
import com.dockmind.common.security.ProblemJsonSecurityHandlers;
import com.dockmind.common.security.Roles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Any role reads disruptions and audit trails; dispatchers and admins report, approve and reject
 * (large plans additionally need ADMIN, checked in the service). Everything else is denied.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        ProblemJsonSecurityHandlers problems = new ProblemJsonSecurityHandlers();
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/disruptions", "/api/v1/disruptions/**")
                        .hasAnyRole(Roles.VIEWER, Roles.DISPATCHER, Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/disruptions", "/api/v1/disruptions/*/approve",
                                "/api/v1/disruptions/*/reject")
                        .hasAnyRole(Roles.DISPATCHER, Roles.ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new DockMindJwtConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems));
        return http.build();
    }
}
