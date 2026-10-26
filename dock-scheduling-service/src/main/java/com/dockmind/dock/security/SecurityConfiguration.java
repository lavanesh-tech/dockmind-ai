package com.dockmind.dock.security;

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
 * Who may call what in Dock Scheduling: any role reads docks and schedules, dispatchers and
 * admins compute schedules, and only admins register docks. Everything else is denied.
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
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/docks", "/api/v1/schedules/**")
                        .hasAnyRole(Roles.VIEWER, Roles.DISPATCHER, Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/docks").hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/schedules/*/compute", "/api/v1/schedules/*/apply")
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
