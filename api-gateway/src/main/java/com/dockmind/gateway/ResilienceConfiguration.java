package com.dockmind.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit-breaker and timeout settings for every backend.
 *
 * <ul>
 *   <li><b>Timeout</b> ({@code dockmind.gateway.timeout}): a call slower than this is cut off and the
 *       caller gets a 503 at once, so slowness in one service never spreads to the gateway.</li>
 *   <li><b>Circuit breaker</b>: over the last 20 calls, if at least half failed or timed out, the
 *       circuit opens for 15 s and calls fail fast without touching the struggling service, giving
 *       it room to recover. Then 3 trial calls decide whether to close it again.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ResilienceConfiguration {

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreaker(GatewayProperties props) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(
                        RoutesConfiguration.RECOVERY.equals(id) || RoutesConfiguration.AI.equals(id)
                                ? props.longTimeout() : props.timeout()).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .build());
    }
}
