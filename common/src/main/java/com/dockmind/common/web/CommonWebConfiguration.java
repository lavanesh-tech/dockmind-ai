package com.dockmind.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared web beans. Each service opts in with {@code @Import(CommonWebConfiguration.class)},
 * which keeps the dependency explicit and visible in the service's main class.
 *
 * <p>{@code proxyBeanMethods = false} tells Spring these {@code @Bean} methods never call
 * each other, so it can skip generating a CGLIB subclass. That makes startup slightly faster.
 */
@Configuration(proxyBeanMethods = false)
public class CommonWebConfiguration {

    /** Spring Boot registers every {@code Filter} bean with the servlet container automatically. */
    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
