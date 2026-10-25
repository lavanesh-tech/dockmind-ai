package com.dockmind.recovery.client;

import com.dockmind.common.web.ApiException;
import com.dockmind.recovery.RecoveryProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Calls Dock Scheduling on behalf of the user: the caller's own token is relayed, so Dock
 * Scheduling applies its own tenant and role checks (this service is not trusted with more
 * rights than the person using it).
 *
 * <p>Every call has a connect and read timeout and goes through a circuit breaker. Only
 * failures of Dock Scheduling itself (5xx, timeouts, connection errors) count towards opening
 * the circuit; a 4xx is the caller's problem and is passed back as is.
 */
@Component
public class DockSchedulingClient {

    private static final Logger log = LoggerFactory.getLogger(DockSchedulingClient.class);

    private final RestClient http;
    private final CircuitBreaker breaker;

    public DockSchedulingClient(RecoveryProperties properties) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(properties.readTimeout());
        this.http = RestClient.builder()
                .baseUrl(properties.dockSchedulingUrl().toString())
                .requestFactory(factory)
                .build();
        this.breaker = CircuitBreaker.of("dock-scheduling", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .ignoreExceptions(ApiException.class)
                .build());
    }

    public DockSnapshot snapshot(LocalDate date, String bearerToken) {
        return call(() -> http.get()
                .uri("/api/v1/schedules/{date}/snapshot", date)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .body(DockSnapshot.class));
    }

    public AppliedSchedule apply(LocalDate date, ApplyRequest request, String bearerToken) {
        return call(() -> http.post()
                .uri("/api/v1/schedules/{date}/apply", date)
                .header("Authorization", "Bearer " + bearerToken)
                .body(request)
                .retrieve()
                .body(AppliedSchedule.class));
    }

    CircuitBreaker.State circuitState() {
        return breaker.getState();
    }

    private <T> T call(Supplier<T> request) {
        try {
            return breaker.executeSupplier(() -> {
                try {
                    return request.get();
                } catch (HttpClientErrorException e) {
                    throw translate(e);
                }
            });
        } catch (ApiException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw unavailable("Dock Scheduling is temporarily unavailable (circuit open). Try again shortly.");
        } catch (RestClientException e) {
            log.warn("Dock Scheduling call failed: {}", e.getClass().getSimpleName());
            throw unavailable("Dock Scheduling did not respond in time or failed. Try again shortly.");
        }
    }

    /** Pass the dependency's 4xx through with a safe, generic message (its body is not echoed back). */
    private static ApiException translate(HttpClientErrorException e) {
        HttpStatusCode code = e.getStatusCode();
        return switch (code.value()) {
            case 401 -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Dock Scheduling rejected the credentials.");
            case 403 -> ApiException.forbidden("Dock Scheduling did not allow this action for your role.");
            case 404 -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "No schedule exists for that date. Compute one first.");
            case 409 -> ApiException.conflict("The schedule changed since this plan was made, or the plan no longer fits it. "
                    + "Report the disruption again to get new plans.");
            default -> ApiException.badRequest("Dock Scheduling rejected the request (" + code.value() + ").");
        };
    }

    private static ApiException unavailable(String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable", detail);
    }
}
